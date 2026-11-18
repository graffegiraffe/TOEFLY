package com.toefly.assessment.adapter.out.client;

import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.port.out.AiUnavailableException;
import com.toefly.assessment.application.port.out.GradingRequest;
import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import com.toefly.assessment.domain.grading.BulletCoverage;
import com.toefly.assessment.domain.grading.PassOutput;
import com.toefly.assessment.domain.grading.RawComment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * One pass of WritingGrader through {@code POST /internal/v1/ai/chat/completions} of ai-gateway (docs/05 §6.15,
 * §9.3.8). 429 and 503 carry Retry-After; every failure becomes {@link AiUnavailableException} so the version is
 * deferred, never lost.
 */
public class AiGatewayGradingAdapter implements AiGradingPort {

    static final long MAX_WAIT_MS = 30_000;
    private static final tools.jackson.databind.json.JsonMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private final RestClient ai;

    public AiGatewayGradingAdapter(RestClient ai) {
        this.ai = ai;
    }

    @Override
    public PassOutput grade(GradingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("useCase", "WRITING_GRADING");
        body.put("promptKey", request.promptKey());
        body.put("promptVersion", request.promptVersion());
        body.put("variables", request.variables());
        body.put("userId", request.userId());
        body.put("allowList", request.allowList());
        body.put("feature", "writing-review");
        body.put("priority", "GRADING");
        body.put("maxWaitMs", MAX_WAIT_MS);
        body.put("idempotencyKey", request.idempotencyKey());
        body.put("passTag", request.passTag());
        JsonNode response;
        try {
            String raw = ai.post()
                    .uri("/internal/v1/ai/chat/completions")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            response = raw == null || raw.isBlank() ? null : JSON.readTree(raw);
        } catch (RestClientResponseException failure) {
            HttpStatusCode status = failure.getStatusCode();
            boolean permanent = status.value() == 400 || status.value() == 422 || status.value() == 404;
            throw new AiUnavailableException(
                    status.value() + " " + problemType(failure.getResponseBodyAsString()),
                    retryAfter(
                            failure.getResponseHeaders() == null
                                    ? null
                                    : failure.getResponseHeaders().getFirst("Retry-After")),
                    permanent,
                    failure);
        } catch (RestClientException | tools.jackson.core.JacksonException failure) {
            throw new AiUnavailableException("ai-gateway unreachable: " + failure.getMessage(), null, false, failure);
        }
        if (response == null
                || response.path("output").isMissingNode()
                || response.path("output").isNull()) {
            throw new AiUnavailableException("ai-gateway returned no structured output", null, true);
        }
        return pass(request.passTag(), response);
    }

    static PassOutput pass(String passTag, JsonNode response) {
        JsonNode out = response.path("output");
        JsonNode stamp = response.path("aiStamp");
        List<CriterionScore> criteria = new ArrayList<>();
        for (String name : CriterionScore.WRITING) {
            JsonNode c = out.path("criteria").path(name);
            criteria.add(new CriterionScore(
                    name, clamp(c.path("level").asInt(0)), c.path("note").asString("")));
        }
        List<BulletCoverage> bullets = new ArrayList<>();
        for (JsonNode b : out.path("bulletCoverage")) {
            bullets.add(new BulletCoverage(
                    b.path("bullet").asInt(),
                    BulletCoverage.Status.valueOf(b.path("status").asString("MISSING"))));
        }
        List<RawComment> comments = new ArrayList<>();
        for (JsonNode c : out.path("comments")) {
            comments.add(new RawComment(
                    c.path("quote").asString(""),
                    c.path("occurrence").asInt(1),
                    category(c.path("category").asString("GRAMMAR")),
                    c.path("subcategory").asString("OTHER"),
                    severity(c.path("severity").asString("MINOR")),
                    c.path("comment").asString(""),
                    c.path("suggestion").isMissingNode()
                            ? null
                            : c.path("suggestion").asString(null)));
        }
        return new PassOutput(
                passTag,
                clamp(out.path("score").asInt(0)),
                criteria,
                bullets,
                comments,
                strings(out.path("strengths")),
                strings(out.path("priorities")),
                strings(out.path("integrityFlags")),
                out.path("scoreRationale").asString(null),
                stamp.path("promptKey").asString(""),
                stamp.path("promptVersion").asInt(0),
                stamp.path("provider").asString(""),
                stamp.path("model").asString(""),
                stamp.path("degraded").asBoolean(false));
    }

    /** A category outside the Writing list is mapped to the closest one; punctuation and register are grammar. */
    static MistakeCategory category(String raw) {
        String value = raw.strip().toUpperCase(java.util.Locale.ROOT);
        for (MistakeCategory category : List.of(
                MistakeCategory.GRAMMAR,
                MistakeCategory.VOCABULARY,
                MistakeCategory.SPELLING,
                MistakeCategory.COHERENCE,
                MistakeCategory.TASK_RESPONSE)) {
            if (value.startsWith(category.name())
                    || value.startsWith(category.name().substring(0, 5))) {
                return category;
            }
        }
        if (value.startsWith("WORD") || value.startsWith("LEXI") || value.startsWith("COLLOC")) {
            return MistakeCategory.VOCABULARY;
        }
        if (value.startsWith("ORGAN") || value.startsWith("COHES") || value.startsWith("TRANSIT")) {
            return MistakeCategory.COHERENCE;
        }
        if (value.startsWith("TASK") || value.startsWith("CONTENT") || value.startsWith("DEVELOP")) {
            return MistakeCategory.TASK_RESPONSE;
        }
        return MistakeCategory.GRAMMAR;
    }

    static Severity severity(String raw) {
        String value = raw.strip().toUpperCase(java.util.Locale.ROOT);
        for (Severity severity : Severity.values()) {
            if (value.startsWith(severity.name())) {
                return severity;
            }
        }
        return Severity.MINOR;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(5, level));
    }

    static @Nullable Duration retryAfter(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.strip()));
        } catch (NumberFormatException notSeconds) {
            return null;
        }
    }

    private static String problemType(String body) {
        int at = body.indexOf("/problems/");
        if (at < 0) {
            return "";
        }
        int end = body.indexOf('"', at);
        return end < 0 ? "" : body.substring(at + "/problems/".length(), end);
    }
}
