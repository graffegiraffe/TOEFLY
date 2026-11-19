package com.toefly.assessment.evals;

import com.toefly.assessment.adapter.out.client.AiGatewayGradingAdapter;
import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.port.out.GradingRequest;
import com.toefly.assessment.domain.grading.PassOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The live ai-gateway for evals and for recording test fixtures: the service token of {@code svc-assessment-service}
 * from Keycloak, {@code POST /internal/v1/ai/chat/completions}, and every raw answer saved next to its request key.
 */
public final class LiveGateway implements AiGradingPort {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String aiGatewayUrl;
    private final String tokenUrl;
    private final String clientSecret;
    private final @Nullable Path recordings;
    private @Nullable String token;
    private long tokenAt;

    public LiveGateway(String aiGatewayUrl, String tokenUrl, String clientSecret, @Nullable Path recordings) {
        this.aiGatewayUrl = aiGatewayUrl;
        this.tokenUrl = tokenUrl;
        this.clientSecret = clientSecret;
        this.recordings = recordings;
    }

    public static LiveGateway fromEnvironment(@Nullable Path recordings) {
        return new LiveGateway(
                env("TOEFLY_AI_GATEWAY_URL", "http://localhost:8095"),
                env("TOEFLY_TOKEN_URL", "http://localhost:8180/realms/toefly/protocol/openid-connect/token"),
                env("SVC_ASSESSMENT_SERVICE_SECRET", "svc-assessment-service-local"),
                recordings);
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
        body.put("feature", "evals");
        body.put("priority", "GRADING");
        body.put("maxWaitMs", 120_000);
        body.put("idempotencyKey", request.idempotencyKey());
        body.put("passTag", request.passTag());
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = post(aiGatewayUrl + "/internal/v1/ai/chat/completions", body);
            if (response.statusCode() == 401 && attempt < 6) {
                synchronized (this) {
                    token = null;
                }
                sleep(5L * attempt);
                continue;
            }
            if (response.statusCode() == 200) {
                record(request.idempotencyKey(), response.body());
                return AiGatewayGradingAdapter.pass(request.passTag(), EvalDataset.JSON.readTree(response.body()));
            }
            if ((response.statusCode() == 429 || response.statusCode() == 503) && attempt < 6) {
                sleep(response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(20L));
                continue;
            }
            throw new IllegalStateException("ai-gateway " + response.statusCode() + ": " + response.body());
        }
    }

    /** Sends a report to {@code POST /api/v1/admin/ai/eval-runs} of ai-gateway (docs/06-ai-agent.md §7.4). */
    public JsonNode postReport(Map<String, Object> report) {
        HttpResponse<String> response = post(aiGatewayUrl + "/api/v1/admin/ai/eval-runs", report);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("eval-runs " + response.statusCode() + ": " + response.body());
        }
        return EvalDataset.JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String url, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(6))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(EvalDataset.JSON.writeValueAsString(body)))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private synchronized String token() throws IOException, InterruptedException {
        if (token == null || System.nanoTime() - tokenAt > java.time.Duration.ofMinutes(4).toNanos()) {
            tokenAt = System.nanoTime();
            String form = "grant_type=client_credentials&client_id=svc-assessment-service&scope=internal&client_secret="
                    + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(tokenUrl))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            String fresh = EvalDataset.JSON.readTree(response.body()).path("access_token").asString("");
            if (response.statusCode() != 200 || fresh.isBlank()) {
                token = null;
                throw new IllegalStateException("Keycloak token " + response.statusCode());
            }
            token = fresh;
        }
        return token;
    }

    private void record(String key, String body) {
        if (recordings == null) {
            return;
        }
        try {
            Files.createDirectories(recordings);
            Files.writeString(recordings.resolve(key.replace(':', '_') + ".json"), body);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(Math.min(seconds, 90)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
