package com.toefly.vocabulary.adapter.out.client;

import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.AiUnavailableException;
import com.toefly.vocabulary.application.port.out.KeyWord;
import com.toefly.vocabulary.domain.entry.WordDetails;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Word card and key words through {@code POST /internal/v1/ai/chat/completions} of ai-gateway (docs/05 §9.3.8),
 * interactive group (20 s read timeout, the gateway waits for quota at most {@code maxWaitMs}). Every failure becomes
 * {@link AiUnavailableException}; the gateway caches the answers by prompt and variables.
 */
public class AiGatewayDictionaryAdapter implements AiDictionaryPort {

    static final String COMPLETIONS = "/internal/v1/ai/chat/completions";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient ai;
    private final Prompts prompts;

    public AiGatewayDictionaryAdapter(RestClient ai, Prompts prompts) {
        this.ai = ai;
        this.prompts = prompts;
    }

    /** Prompt keys and use cases of the two calls (ai-gateway prompt registry). */
    public record Prompts(String wordCardUseCase, String wordCardKey, String keyWordsUseCase, String keyWordsKey) {}

    @Override
    public WordCard wordCard(String term, @Nullable String context, UUID userId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("term", term);
        variables.put("context", context == null ? "" : context);
        JsonNode response =
                call(prompts.wordCardUseCase(), prompts.wordCardKey(), variables, userId, "vocabulary-card", 4_000);
        return card(term, response);
    }

    @Override
    public KeyWords keyWords(String text, int max, UUID userId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("text", text);
        variables.put("maxWords", max);
        JsonNode response = call(
                prompts.keyWordsUseCase(), prompts.keyWordsKey(), variables, userId, "vocabulary-key-words", 10_000);
        return keyWords(response, max);
    }

    static WordCard card(String term, JsonNode response) {
        JsonNode out = response.path("output");
        WordDetails details = new WordDetails(
                text(out, "partOfSpeech"),
                text(out, "definitionEn"),
                text(out, "exampleEn"),
                text(out, "translationRu"),
                text(out, "ipa"));
        String lemma = text(out, "lemma");
        return new WordCard(lemma == null ? term : lemma, details, JSON.writeValueAsString(response.path("aiStamp")));
    }

    static KeyWords keyWords(JsonNode response, int max) {
        List<KeyWord> words = new ArrayList<>();
        for (JsonNode word : response.path("output").path("words")) {
            String lemma = text(word, "lemma");
            if (lemma != null && words.size() < max) {
                words.add(new KeyWord(
                        lemma, text(word, "partOfSpeech"), text(word, "definitionEn"), text(word, "sentence")));
            }
        }
        return new KeyWords(words, JSON.writeValueAsString(response.path("aiStamp")));
    }

    private JsonNode call(
            String useCase, String promptKey, Map<String, Object> variables, UUID userId, String feature, long wait) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("useCase", useCase);
        body.put("promptKey", promptKey);
        body.put("variables", variables);
        body.put("userId", userId);
        body.put("feature", feature);
        body.put("priority", "INTERACTIVE");
        body.put("maxWaitMs", wait);
        body.put("cachePolicy", "READ_WRITE");
        JsonNode response;
        try {
            String raw = ai.post()
                    .uri(COMPLETIONS)
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
        return response;
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString("").strip();
        return text.isEmpty() ? null : text;
    }
}
