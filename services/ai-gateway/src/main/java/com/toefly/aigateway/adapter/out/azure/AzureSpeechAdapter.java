package com.toefly.aigateway.adapter.out.azure;

import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.SpeechPort;
import com.toefly.aigateway.domain.speech.AssessedWord;
import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.AssessmentRequest;
import com.toefly.aigateway.domain.speech.Phoneme;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Azure AI Speech over REST (docs/12, phase 5a, decision 3): pronunciation assessment through the short-audio
 * recognition endpoint with the {@code Pronunciation-Assessment} header, neural TTS through
 * {@code cognitiveservices/v1}. Offsets come in 100-ns ticks.
 */
public class AzureSpeechAdapter implements SpeechPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long TICKS_PER_MS = 10_000;

    private final RestClient stt;
    private final RestClient tts;
    private final String key;

    /** Azure wants this exact value, which is not a valid MIME type for Spring; it is set after serialisation. */
    static final String WAV_PCM_16K = "audio/wav; codecs=audio/pcm; samplerate=16000";

    public AzureSpeechAdapter(RestClient stt, RestClient tts, String key) {
        this.stt = stt.mutate()
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("Content-Type", WAV_PCM_16K);
                    return execution.execute(request, body);
                })
                .build();
        this.tts = tts;
        this.key = key;
    }

    @Override
    public Assessment assess(byte[] wav, AssessmentRequest request) {
        requireKey();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(
                "ReferenceText", request.scripted() && request.referenceText() != null ? request.referenceText() : "");
        config.put("GradingSystem", "HundredMark");
        config.put("Granularity", request.granularity());
        config.put("Dimension", "Comprehensive");
        config.put("EnableMiscue", request.scripted() && request.miscue());
        config.put("EnableProsodyAssessment", request.prosody());
        String header = Base64.getEncoder()
                .encodeToString(JSON.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
        JsonNode body;
        try {
            body = stt.post()
                    .uri(uri -> uri.path("/speech/recognition/conversation/cognitiveservices/v1")
                            .queryParam("language", request.locale())
                            .queryParam("format", "detailed")
                            .build())
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Pronunciation-Assessment", header)
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(wav)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException error) {
            throw map(error);
        }
        if (body == null || !"Success".equals(body.path("RecognitionStatus").asString(""))) {
            throw new ProviderFailure(
                    ProviderFailure.Kind.REJECTED,
                    "Azure did not recognise speech: " + (body == null ? "empty" : body.path("RecognitionStatus")),
                    null,
                    null);
        }
        return assessment(body, request.scripted());
    }

    @Override
    public byte[] synthesize(String ssml, String format) {
        requireKey();
        try {
            byte[] audio = tts.post()
                    .uri("/cognitiveservices/v1")
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("X-Microsoft-OutputFormat", format)
                    .header("User-Agent", "toefly-ai-gateway")
                    .contentType(MediaType.parseMediaType("application/ssml+xml"))
                    .body(ssml)
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw ProviderFailure.unavailable("Azure TTS answered no audio", null);
            }
            return audio;
        } catch (ProviderFailure failure) {
            throw failure;
        } catch (RuntimeException error) {
            throw map(error);
        }
    }

    static Assessment assessment(JsonNode body, boolean scripted) {
        JsonNode best = body.path("NBest").path(0);
        JsonNode scores = best.has("PronunciationAssessment") ? best.path("PronunciationAssessment") : best;
        List<AssessedWord> words = new ArrayList<>();
        for (JsonNode word : best.path("Words")) {
            JsonNode assessed = word.path("PronunciationAssessment");
            List<Phoneme> phonemes = new ArrayList<>();
            for (JsonNode phoneme : word.path("Phonemes")) {
                phonemes.add(new Phoneme(
                        phoneme.path("Phoneme").asString(""),
                        phoneme.path("PronunciationAssessment")
                                .path("AccuracyScore")
                                .asDouble()));
            }
            words.add(new AssessedWord(
                    word.path("Word").asString(""),
                    assessed.path("AccuracyScore").asDouble(),
                    assessed.path("ErrorType").asString("None"),
                    word.path("Offset").asLong() / TICKS_PER_MS,
                    word.path("Duration").asLong() / TICKS_PER_MS,
                    phonemes));
        }
        return new Assessment(
                scores.path("AccuracyScore").asDouble(),
                scores.path("FluencyScore").asDouble(),
                scripted && scores.has("CompletenessScore")
                        ? scores.path("CompletenessScore").asDouble()
                        : null,
                scores.has("ProsodyScore") ? scores.path("ProsodyScore").asDouble() : null,
                scores.path("PronScore").asDouble(),
                best.path("Display").asString(body.path("DisplayText").asString("")),
                body.path("Duration").asLong() / (TICKS_PER_MS * 1000.0),
                words);
    }

    private void requireKey() {
        if (key.isBlank()) {
            throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "AZURE_SPEECH_KEY is not set", null, null);
        }
    }

    private static ProviderFailure map(RuntimeException error) {
        if (error instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            @Nullable Duration retryAfter = null;
            String header = http.getResponseHeaders() == null
                    ? null
                    : http.getResponseHeaders().getFirst("Retry-After");
            if (header != null) {
                try {
                    retryAfter = Duration.ofSeconds(Long.parseLong(header.strip()));
                } catch (NumberFormatException ignored) {
                    retryAfter = null;
                }
            }
            if (status == 429) {
                return new ProviderFailure(ProviderFailure.Kind.RATE_LIMITED, "Azure answered 429", retryAfter, error);
            }
            if (status >= 500) {
                return ProviderFailure.unavailable("Azure answered " + status, error);
            }
            return new ProviderFailure(ProviderFailure.Kind.REJECTED, "Azure answered " + status, null, error);
        }
        return ProviderFailure.unavailable("Azure is unreachable: " + error.getMessage(), error);
    }
}
