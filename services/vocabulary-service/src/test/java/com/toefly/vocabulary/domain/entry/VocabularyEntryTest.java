package com.toefly.vocabulary.domain.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocabularyEntryTest {

    static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("AC-15.01.2: a saved word keeps the lemma, the sentence and the ItemSet it came from")
    void create() {
        UUID itemSet = UUID.randomUUID();
        VocabularyEntry entry = VocabularyEntry.create(
                UUID.randomUUID(),
                USER,
                "  Mitigate, ",
                WordDetails.EMPTY,
                new EntryContext("Trees  mitigate the heat.", itemSet, "Urban forests"),
                EntryOrigin.TAP,
                null,
                NOW);

        assertThat(entry.lemma()).isEqualTo("mitigate");
        assertThat(entry.contexts()).singleElement().satisfies(context -> {
            assertThat(context.sentence()).isEqualTo("Trees mitigate the heat.");
            assertThat(context.itemSetId()).isEqualTo(itemSet);
        });
        assertThat(entry.enriched()).isFalse();
    }

    @Test
    @DisplayName("AC-15.01.3: saving the word again adds a context, at most 5, the oldest is dropped")
    void addContext() {
        VocabularyEntry entry = VocabularyEntry.create(
                UUID.randomUUID(), USER, "allocate", WordDetails.EMPTY, null, EntryOrigin.TAP, null, NOW);
        for (int i = 1; i <= 7; i++) {
            entry = entry.addContext(new EntryContext("Sentence " + i, null, "Text"), NOW);
        }
        entry = entry.addContext(new EntryContext("sentence 7", null, "Text"), NOW);
        entry = entry.addContext(null, NOW);

        assertThat(entry.contexts())
                .extracting(EntryContext::sentence)
                .containsExactly("Sentence 3", "Sentence 4", "Sentence 5", "Sentence 6", "sentence 7");
        assertThat(entry.latestContext().sentence()).isEqualTo("sentence 7");
    }

    @Test
    @DisplayName("AC-15.01.5: enrichment fills the gaps and keeps the learner's own translation")
    void enrich() {
        VocabularyEntry entry = VocabularyEntry.create(
                        UUID.randomUUID(), USER, "allocate", WordDetails.EMPTY, null, EntryOrigin.TAP, null, NOW)
                .edit("распределять (мой)", "note", NOW);
        VocabularyEntry enriched = entry.enrich(
                new WordDetails(
                        "verb", "to give out for a purpose", "Funds were allocated.", "выделять", "/ˈæl.ə.keɪt/"),
                NOW);

        assertThat(enriched.enriched()).isTrue();
        assertThat(enriched.details().translationRu()).isEqualTo("распределять (мой)");
        assertThat(enriched.details().definitionEn()).isEqualTo("to give out for a purpose");
        assertThat(enriched.note()).isEqualTo("note");
        assertThat(enriched.edit(" ", " ", NOW).details().translationRu()).isNull();
    }

    @Test
    @DisplayName("AC-15.01.1: a selection must be a word or a short phrase")
    void lemma() {
        assertThat(Lemma.normalize("“Photosynthesis”")).isEqualTo("photosynthesis");
        assertThat(Lemma.normalize("carry  out")).isEqualTo("carry out");
        assertThat(Lemma.normalize("don’t")).isEqualTo("don't");
        assertThat(Lemma.normalize("well-being")).isEqualTo("well-being");
        assertThatThrownBy(() -> Lemma.normalize("  ...  ")).isInstanceOf(InvalidTermException.class);
        assertThatThrownBy(() -> Lemma.normalize("1984")).isInstanceOf(InvalidTermException.class);
        assertThatThrownBy(() -> Lemma.normalize("one two three four five")).isInstanceOf(InvalidTermException.class);
        assertThatThrownBy(() -> Lemma.normalize("a".repeat(81))).isInstanceOf(InvalidTermException.class);
    }

    @Test
    @DisplayName("WordDetails.orElse keeps the first non-blank value")
    void orElse() {
        WordDetails merged = new WordDetails(null, "def", " ", null, null)
                .orElse(new WordDetails("noun", "other", "ex", "ру", "ipa"));
        assertThat(merged).isEqualTo(new WordDetails("noun", "def", "ex", "ру", "ipa"));
    }
}
