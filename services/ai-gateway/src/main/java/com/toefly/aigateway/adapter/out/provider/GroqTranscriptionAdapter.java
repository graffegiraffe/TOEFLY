package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.TranscriptionPort;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.speech.TranscribedWord;
import com.toefly.aigateway.domain.speech.Transcript;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Whisper on Groq with word timestamps. Spring AI does not pass {@code timestamp_granularities[]}, so this adapter
 * posts the multipart form itself (§2.2.3).
 */
public class GroqTranscriptionAdapter implements TranscriptionPort {

    private final RestClient groq;
    private final String apiKey;

    public GroqTranscriptionAdapter(RestClient groq, String apiKey) {
        this.groq = groq;
        this.apiKey = apiKey;
    }

    @Override
    public Transcript transcribe(
            ModelRef model, byte[] audio, String fileName, String language, @Nullable String prompt) {
        if (apiKey.isBlank()) {
            throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "GROQ_API_KEY is not set", null, null);
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        form.add("model", model.model());
        form.add("language", language);
        form.add("temperature", "0");
        form.add("response_format", "verbose_json");
        form.add("timestamp_granularities[]", "word");
        if (prompt != null && !prompt.isBlank()) {
            form.add("prompt", prompt);
        }
        JsonNode body;
        try {
            body = groq.post()
                    .uri("/openai/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException error) {
            throw ProviderErrors.map("GROQ", error);
        }
        if (body == null) {
            throw ProviderFailure.unavailable("GROQ answered an empty transcription", null);
        }
        List<TranscribedWord> words = new ArrayList<>();
        for (JsonNode word : body.path("words")) {
            words.add(new TranscribedWord(
                    word.path("word").asString("").strip(),
                    word.path("start").asDouble(),
                    word.path("end").asDouble(),
                    word.has("probability") ? word.path("probability").asDouble() : null));
        }
        return new Transcript(
                body.path("text").asString("").strip(),
                words,
                body.path("duration").asDouble());
    }
}
