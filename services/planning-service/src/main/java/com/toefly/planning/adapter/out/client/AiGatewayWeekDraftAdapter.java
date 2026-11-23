package com.toefly.planning.adapter.out.client;

import com.toefly.planning.application.port.out.AiUnavailableException;
import com.toefly.planning.application.port.out.WeekDraftAiPort;
import com.toefly.planning.domain.composition.DraftLayout;
import com.toefly.planning.domain.week.AiStamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * WeekGenerator through {@code POST /internal/v1/ai/chat/completions} of ai-gateway (docs/05 §6.15, §9.3.8): use
 * case {@code WEEK_GENERATION}, background priority. Every failure becomes {@link AiUnavailableException}, so the
 * week falls back to the rules and is never lost.
 */
public class AiGatewayWeekDraftAdapter implements WeekDraftAiPort {

    static final String USE_CASE = "WEEK_GENERATION";
    static final long MAX_WAIT_MS = 60_000;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient ai;

    public AiGatewayWeekDraftAdapter(RestClient ai) {
        this.ai = ai;
    }

    @Override
    public DraftReply draft(DraftCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("useCase", USE_CASE);
        body.put("promptKey", PROMPT_KEY);
        body.put("promptVersion", null);
        body.put("variables", call.variables());
        body.put("userId", call.userId());
        body.put("feature", "week-draft");
        body.put("priority", "BACKGROUND");
        body.put("maxWaitMs", MAX_WAIT_MS);
        body.put("idempotencyKey", call.idempotencyKey());
        JsonNode response;
        try {
            String raw = ai.post()
                    .uri("/internal/v1/ai/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            response = raw == null || raw.isBlank() ? null : JSON.readTree(raw);
        } catch (RestClientResponseException failure) {
            throw new AiUnavailableException(
                    "ai-gateway answered " + failure.getStatusCode().value(), failure);
        } catch (RestClientException | JacksonException failure) {
            throw new AiUnavailableException("ai-gateway unreachable: " + failure.getMessage(), failure);
        }
        if (response == null || !response.path("output").isObject()) {
            throw new AiUnavailableException("ai-gateway returned no structured output");
        }
        return reply(response);
    }

    /** {@code CompletionResponse} with the output of {@code WeekDraftOutput}; missing fields read as empty. */
    public static DraftReply reply(JsonNode response) {
        JsonNode output = response.path("output");
        List<DraftLayout.Day> days = new ArrayList<>();
        for (JsonNode day : output.path("days")) {
            List<DraftLayout.Pick> picks = new ArrayList<>();
            for (JsonNode task : day.path("tasks")) {
                picks.add(new DraftLayout.Pick(
                        task.path("candidateRef").asString(""),
                        task.path("whyForYou").asString("")));
            }
            days.add(new DraftLayout.Day(day.path("day").asString(""), picks));
        }
        JsonNode stamp = response.path("aiStamp");
        return new DraftReply(
                new DraftLayout(
                        days,
                        output.path("weekGoal").asString(""),
                        output.path("focusExplanation").asString("")),
                new AiStamp(
                        stamp.path("aiCallId").asString(""),
                        stamp.path("promptKey").asString(PROMPT_KEY),
                        stamp.path("promptVersion").asInt(0),
                        stamp.path("provider").asString(""),
                        stamp.path("model").asString(""),
                        stamp.path("fallbackDepth").asInt(0),
                        stamp.path("degraded").asBoolean(false)));
    }
}
