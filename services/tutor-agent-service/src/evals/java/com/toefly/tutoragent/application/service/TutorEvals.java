package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.adapter.out.client.AiGatewayAdapter;
import com.toefly.tutoragent.adapter.out.client.GatewayChatModelAdapter;
import com.toefly.tutoragent.adapter.out.client.PromptClientAdapter;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.ChatModelPort;
import com.toefly.tutoragent.application.port.out.PromptPort;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The live side of the tutor evals: ai-gateway with the service token of {@code svc-tutor-agent-service}, the judge
 * and the hashes of the prompt files the report was made with (docs/12, phase 5c, decision 3).
 */
final class TutorEvals {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path PROMPTS = Path.of("../ai-gateway/src/main/resources/prompts");
    static final Path REPORTS = Path.of("src/evals/reports");
    static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String gateway = env("TOEFLY_AI_GATEWAY_URL", "http://localhost:8095");
    private final String tokenUrl = env("TOEFLY_TOKEN_URL", "http://localhost:8180/realms/toefly/protocol/openid-connect/token");
    private final String secret = env("SVC_TUTOR_AGENT_SERVICE_SECRET", "svc-tutor-agent-service-local");
    private String token = "";
    private long tokenAt;

    synchronized String token() {
        if (!token.isEmpty() && System.currentTimeMillis() - tokenAt < 240_000) {
            return token;
        }
        String form = "grant_type=client_credentials&client_id=svc-tutor-agent-service&client_secret="
                + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(tokenUrl))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            token = JSON.readTree(response.body()).path("access_token").asString("");
            if (token.isEmpty()) {
                throw new IllegalStateException("Keycloak gave no token: " + response.statusCode());
            }
            tokenAt = System.currentTimeMillis();
            return token;
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException("Keycloak is unreachable", failure);
        }
    }

    RestClient client() {
        return RestClient.builder()
                .baseUrl(gateway)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(token());
                    return execution.execute(request, body);
                })
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    {
                        setConnectTimeout(Duration.ofSeconds(5));
                        setReadTimeout(Duration.ofSeconds(300));
                    }
                })
                .build();
    }

    AiPort ai() {
        return new AiGatewayAdapter(client());
    }

    ChatModelPort chatModel() {
        return new GatewayChatModelAdapter(gateway, this::token, Duration.ofSeconds(240));
    }

    PromptPort prompts() {
        return new PromptClientAdapter(client(), Clock.systemUTC());
    }

    /** Labels of one answer by the judge (tutor.eval.judge on Z.ai). */
    Map<String, Object> judge(AiPort ai, String situation, String answer) {
        for (int attempt = 1; ; attempt++) {
            try {
                AiPort.Completion completion = ai.complete(new AiPort.CompletionCall(
                        "EVAL_JUDGE",
                        "tutor.eval.judge",
                        null,
                        Map.of("situation", situation, "answer", answer),
                        null,
                        "evals",
                        "BACKGROUND",
                        240_000,
                        null,
                        null));
                if (completion.output() != null) {
                    Map<String, Object> labels = new LinkedHashMap<>(completion.output());
                    labels.put("judgeModel", completion.stamp().provider() + ":" + completion.stamp().model());
                    return labels;
                }
            } catch (RuntimeException failure) {
                if (attempt >= 4) {
                    throw failure;
                }
                sleep(20L * attempt);
            }
        }
    }

    static Map<String, String> promptHashes(List<String> keys) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String key : keys) {
            hashes.put(key, sha256(PROMPTS.resolve(key).resolve("v1.yaml")));
        }
        return hashes;
    }

    static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * True when every number of the answer occurs in the facts the agent had. Allowed without a source: points of the
     * band scale (1–6 in steps of 0.5), small counts up to 10 in advice, and a percent whose fraction is in the facts
     * (40% for an error rate of 0.4).
     */
    static boolean numbersGrounded(String answer, String facts) {
        Matcher m = NUMBER.matcher(answer);
        while (m.find()) {
            String n = m.group();
            double value = Double.parseDouble(n);
            boolean bandPoint = value >= 1 && value <= 6 && value * 2 == Math.rint(value * 2);
            boolean smallCount = value <= 10 && value == Math.rint(value);
            String fraction = Double.toString(value / 100).replaceAll("0+$", "");
            boolean percentOfFraction = facts.contains(fraction.endsWith(".") ? fraction + "0" : fraction);
            if (!facts.contains(n) && !bandPoint && !smallCount && !percentOfFraction) {
                return false;
            }
        }
        return true;
    }

    static JsonNode read(Path file) {
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
