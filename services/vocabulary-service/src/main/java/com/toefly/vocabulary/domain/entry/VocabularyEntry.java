package com.toefly.vocabulary.domain.entry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A word of the personal dictionary (docs/03 §6.11). One entry per {@code (userId, lemma)} (INV-VOC-01); saving the
 * lemma again adds a context, at most {@value #MAX_CONTEXTS}, the oldest is dropped.
 */
public record VocabularyEntry(
        UUID id,
        UUID userId,
        String lemma,
        WordDetails details,
        @Nullable String note,
        List<EntryContext> contexts,
        EntryOrigin origin,
        @Nullable UUID wordSetId,
        boolean enriched,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    public static final int MAX_CONTEXTS = 5;
    public static final int MAX_NOTE = 500;
    public static final int MAX_TRANSLATION = 200;

    public VocabularyEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(lemma, "lemma");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        contexts = List.copyOf(contexts);
    }

    public static VocabularyEntry create(
            UUID id,
            UUID userId,
            String term,
            WordDetails details,
            @Nullable EntryContext context,
            EntryOrigin origin,
            @Nullable UUID wordSetId,
            Instant now) {
        return new VocabularyEntry(
                id,
                userId,
                Lemma.normalize(term),
                details,
                null,
                context == null ? List.of() : List.of(context),
                origin,
                wordSetId,
                details.complete(),
                now,
                now,
                0);
    }

    /** AC-15.01.3: another sighting of the word; a sentence already stored is not repeated. */
    public VocabularyEntry addContext(@Nullable EntryContext context, Instant now) {
        if (context == null || context.sentence().isBlank()) {
            return this;
        }
        List<EntryContext> next = new ArrayList<>(contexts);
        next.removeIf(existing -> existing.sentence().equalsIgnoreCase(context.sentence()));
        next.add(context);
        while (next.size() > MAX_CONTEXTS) {
            next.remove(0);
        }
        return new VocabularyEntry(
                id, userId, lemma, details, note, next, origin, wordSetId, enriched, createdAt, now, version);
    }

    /** Dictionary data from ai-gateway or a word set; the learner's own translation is kept. */
    public VocabularyEntry enrich(WordDetails found, Instant now) {
        WordDetails merged = new WordDetails(
                details.partOfSpeech() != null ? details.partOfSpeech() : found.partOfSpeech(),
                details.definitionEn() != null ? details.definitionEn() : found.definitionEn(),
                details.exampleEn() != null ? details.exampleEn() : found.exampleEn(),
                details.translationRu() != null ? details.translationRu() : found.translationRu(),
                details.ipa() != null ? details.ipa() : found.ipa());
        return new VocabularyEntry(
                id,
                userId,
                lemma,
                merged,
                note,
                contexts,
                origin,
                wordSetId,
                merged.complete(),
                createdAt,
                now,
                version);
    }

    /** "Edit" of the word detail: the Russian translation and a personal note. */
    public VocabularyEntry edit(@Nullable String translationRu, @Nullable String newNote, Instant now) {
        WordDetails edited = translationRu == null
                ? details
                : new WordDetails(
                        details.partOfSpeech(),
                        details.definitionEn(),
                        details.exampleEn(),
                        blankToNull(clip(translationRu, MAX_TRANSLATION)),
                        details.ipa());
        String editedNote = newNote == null ? note : blankToNull(clip(newNote, MAX_NOTE));
        return new VocabularyEntry(
                id, userId, lemma, edited, editedNote, contexts, origin, wordSetId, enriched, createdAt, now, version);
    }

    /** The latest sentence the word was met in, for the cloze format of the review. */
    public @Nullable EntryContext latestContext() {
        return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
    }

    private static String clip(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    private static @Nullable String blankToNull(String text) {
        return text.isBlank() ? null : text;
    }
}
