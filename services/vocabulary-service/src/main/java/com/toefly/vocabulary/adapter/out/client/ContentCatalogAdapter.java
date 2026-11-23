package com.toefly.vocabulary.adapter.out.client;

import com.toefly.vocabulary.application.port.in.DependencyUnavailableException;
import com.toefly.vocabulary.application.port.out.ContentCatalogPort;
import com.toefly.vocabulary.domain.wordset.WordSet;
import com.toefly.vocabulary.domain.wordset.WordSetWord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * content-service with the service token of {@code svc-vocabulary-service}: approved Collins Vocabulary units
 * ({@code GET /api/v1/vocabulary-units}, as planning reads them) and the text of an ItemSet
 * ({@code GET /internal/v1/item-sets/{id}}, without keys).
 */
public class ContentCatalogAdapter implements ContentCatalogPort {

    private final RestClient content;

    public ContentCatalogAdapter(RestClient content) {
        this.content = content;
    }

    @Override
    public List<WordSet> vocabularyUnits() {
        JsonNode body;
        try {
            body = content.get().uri("/api/v1/vocabulary-units").retrieve().body(JsonNode.class);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException("content-service", failure);
        }
        List<WordSet> sets = new ArrayList<>();
        if (body == null) {
            return sets;
        }
        for (JsonNode unit : body.path("items")) {
            if (!unit.path("status").asString("APPROVED").equals("APPROVED")) {
                continue;
            }
            int unitNo = unit.path("unitNo").asInt();
            if (unitNo < 1 || unitNo > 20) {
                continue;
            }
            List<WordSetWord> words = new ArrayList<>();
            for (JsonNode word : unit.path("words")) {
                String lemma = word.path("word").asString("").strip();
                String definition = word.path("definition").asString("").strip();
                if (lemma.isEmpty() || definition.isEmpty()) {
                    continue;
                }
                words.add(new WordSetWord(
                        lemma,
                        word.path("pos").asString("").strip(),
                        definition,
                        blankToNull(word.path("example").asString(null)),
                        blankToNull(word.path("ipa").asString(null))));
            }
            sets.add(new WordSet(
                    UUID.fromString(unit.path("id").asString()),
                    unitNo,
                    unit.path("theme").asString("GENERAL"),
                    unit.path("title").asString("Unit " + unitNo),
                    words));
        }
        return sets;
    }

    @Override
    public Optional<ItemSetText> itemSetText(UUID itemSetId) {
        JsonNode set;
        try {
            set = content.get()
                    .uri("/internal/v1/item-sets/{id}", itemSetId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException failure) {
            if (failure.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new DependencyUnavailableException("content-service", failure);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException("content-service", failure);
        }
        if (set == null) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        collect(set.path("payload").path("stimulus"), text);
        if (text.isEmpty()) {
            collect(set.path("payload"), text);
        }
        String title = set.path("title").asString("");
        return text.isEmpty()
                ? Optional.empty()
                : Optional.of(new ItemSetText(title, text.toString().strip()));
    }

    /** Passage, transcript or prompt text: every long string of the stimulus, in document order. */
    static void collect(JsonNode node, StringBuilder text) {
        if (node.isString()) {
            String value = node.asString();
            if (value.length() >= 40 && value.contains(" ") && !value.startsWith("http")) {
                text.append(value).append("\n\n");
            }
        } else if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                collect(child, text);
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
