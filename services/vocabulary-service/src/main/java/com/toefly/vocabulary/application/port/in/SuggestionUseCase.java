package com.toefly.vocabulary.application.port.in;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** "Key words" of a text (US-15.02). */
public interface SuggestionUseCase {

    List<Suggestion> suggest(UUID userId, @Nullable UUID itemSetId, @Nullable String text);

    record Suggestion(
            String lemma,
            @Nullable String partOfSpeech,
            @Nullable String definitionEn,
            @Nullable String sentence,
            boolean inDictionary) {}
}
