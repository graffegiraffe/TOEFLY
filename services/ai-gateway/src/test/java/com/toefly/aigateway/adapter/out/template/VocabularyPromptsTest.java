package com.toefly.aigateway.adapter.out.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.adapter.out.schema.JsonOutputSchemaAdapter;
import com.toefly.aigateway.application.port.out.PromptSourcePort;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("prompts of vocabulary-service (docs/12 phase 8, US-15.01, US-15.02)")
class VocabularyPromptsTest {

    final Map<String, PromptVersion> prompts = new PromptFileSource("classpath:prompts", Clock.systemUTC())
            .load().stream()
                    .map(PromptSourcePort.Source::version)
                    .filter(v -> v.key().startsWith("vocabulary."))
                    .collect(Collectors.toMap(v -> v.key() + ":" + v.version(), Function.identity()));
    final JsonOutputSchemaAdapter schemas = new JsonOutputSchemaAdapter();

    @Test
    @DisplayName("AC-15.01.5: the word card prompt treats the sentence as data and checks the recorded answer")
    void wordCard() throws IOException {
        PromptVersion card = prompts.get("vocabulary.word-card:1");
        assertThat(card.useCase()).isEqualTo(AiUseCase.WORD_CARD);
        assertThat(card.untrusted()).containsExactly("context");
        assertThat(card.user()).contains("$term$", "$context$");
        String recorded = fixture("word-card-output.json");
        assertThat(schemas.validate(card.outputSchema(), schemas.parse(recorded)))
                .isEmpty();
        assertThat(schemas.validate(card.outputSchema(), schemas.parse(recorded.replace("\"verb\"", "\"thing\""))))
                .isNotEmpty();
    }

    @Test
    @DisplayName("AC-15.02.1: the key words prompt returns at most ten words with their sentences")
    void keyWords() throws IOException {
        PromptVersion words = prompts.get("vocabulary.key-words:1");
        assertThat(words.useCase()).isEqualTo(AiUseCase.KEY_WORDS);
        assertThat(words.untrusted()).containsExactly("text");
        assertThat(words.system()).contains("$maxWords$");
        String recorded = fixture("key-words-output.json");
        assertThat(schemas.validate(words.outputSchema(), schemas.parse(recorded)))
                .isEmpty();
        assertThat(schemas.validate(
                        words.outputSchema(), schemas.parse(recorded.replace("\"sentence\"", "\"example\""))))
                .isNotEmpty();
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/vocabulary-fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
