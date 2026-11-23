package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.SuggestionUseCase;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.AiUnavailableException;
import com.toefly.vocabulary.application.port.out.ContentCatalogPort;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.KeyWord;
import com.toefly.vocabulary.application.port.out.KeyWordCachePort;
import com.toefly.vocabulary.domain.entry.InvalidTermException;
import com.toefly.vocabulary.domain.entry.Lemma;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * "Key words" (AC-15.02.1): 5–10 academic words of a text chosen by ai-gateway, cached per ItemSet for all learners;
 * words already in the learner's dictionary are left out.
 */
@Service
public class SuggestionService implements SuggestionUseCase {

    static final int MAX_WORDS = 10;
    static final int MAX_TEXT = 12_000;

    private final AiDictionaryPort ai;
    private final KeyWordCachePort cache;
    private final ContentCatalogPort content;
    private final EntryRepository entries;

    public SuggestionService(
            AiDictionaryPort ai, KeyWordCachePort cache, ContentCatalogPort content, EntryRepository entries) {
        this.ai = ai;
        this.cache = cache;
        this.content = content;
        this.entries = entries;
    }

    @Override
    public List<Suggestion> suggest(UUID userId, @Nullable UUID itemSetId, @Nullable String text) {
        List<KeyWord> words;
        if (itemSetId != null) {
            words = forItemSet(userId, itemSetId);
        } else if (text != null && !text.isBlank()) {
            words = ask(clip(text), userId).words();
        } else {
            throw VocabularyException.invalid("Send itemSetId or text.");
        }
        Map<String, KeyWord> unique = new LinkedHashMap<>();
        for (KeyWord word : words) {
            try {
                unique.putIfAbsent(Lemma.normalize(word.lemma()), word);
            } catch (InvalidTermException skipped) {
                // the model returned something that is not a word
            }
        }
        Set<String> known = entries.existingLemmas(userId, unique.keySet());
        List<Suggestion> result = new ArrayList<>();
        for (Map.Entry<String, KeyWord> word : unique.entrySet()) {
            if (result.size() == MAX_WORDS) {
                break;
            }
            if (!known.contains(word.getKey())) {
                KeyWord value = word.getValue();
                result.add(new Suggestion(
                        word.getKey(), value.partOfSpeech(), value.definitionEn(), value.sentence(), false));
            }
        }
        return result;
    }

    private List<KeyWord> forItemSet(UUID userId, UUID itemSetId) {
        Optional<List<KeyWord>> cached = cache.find(itemSetId);
        if (cached.isPresent()) {
            return cached.get();
        }
        ContentCatalogPort.ItemSetText source = content.itemSetText(itemSetId)
                .orElseThrow(() -> VocabularyException.notFound("The text of this task is not available."));
        AiDictionaryPort.KeyWords found = ask(clip(source.text()), userId);
        if (!found.words().isEmpty()) {
            cache.put(itemSetId, found.words(), found.aiStamp());
        }
        return found.words();
    }

    private AiDictionaryPort.KeyWords ask(String text, UUID userId) {
        try {
            return ai.keyWords(text, MAX_WORDS, userId);
        } catch (AiUnavailableException unavailable) {
            throw VocabularyException.aiUnavailable();
        }
    }

    private static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
