package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.AddWordResult;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.in.WordSetUseCase;
import com.toefly.vocabulary.application.port.in.WordSetView;
import com.toefly.vocabulary.application.port.out.ContentCatalogPort;
import com.toefly.vocabulary.application.port.out.DictionaryCachePort;
import com.toefly.vocabulary.application.port.out.WordSetRepository;
import com.toefly.vocabulary.domain.entry.EntryContext;
import com.toefly.vocabulary.domain.entry.EntryOrigin;
import com.toefly.vocabulary.domain.entry.InvalidTermException;
import com.toefly.vocabulary.domain.entry.Lemma;
import com.toefly.vocabulary.domain.wordset.WordSet;
import com.toefly.vocabulary.domain.wordset.WordSetWord;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Collins Vocabulary units as word sets (US-15.04); the words carry their own definitions, no AI is needed. */
@Service
public class WordSetService implements WordSetUseCase {

    private static final Logger log = LoggerFactory.getLogger(WordSetService.class);

    private final WordSetRepository sets;
    private final ContentCatalogPort content;
    private final DictionaryCachePort cache;
    private final EntryWriter writer;
    private final Clock clock;

    public WordSetService(
            WordSetRepository sets,
            ContentCatalogPort content,
            DictionaryCachePort cache,
            EntryWriter writer,
            Clock clock) {
        this.sets = sets;
        this.content = content;
        this.cache = cache;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public List<WordSetView> list(UUID userId) {
        Map<UUID, WordSetRepository.Progress> progress = sets.progress(userId);
        return sets.all().stream()
                .sorted(Comparator.comparingInt(WordSet::unitNo))
                .map(set -> view(set, progress))
                .toList();
    }

    @Override
    public WordSetView get(UUID userId, UUID wordSetId) {
        WordSet set = sets.find(wordSetId).orElseThrow(WordSetService::notFound);
        return view(set, sets.progress(userId));
    }

    @Override
    public Enrollment enroll(UUID userId, UUID wordSetId) {
        WordSet set = sets.find(wordSetId).orElseThrow(WordSetService::notFound);
        Instant now = clock.instant();
        int added = 0;
        int present = 0;
        for (WordSetWord word : set.words()) {
            String lemma;
            try {
                lemma = Lemma.normalize(word.lemma());
            } catch (InvalidTermException invalid) {
                log.warn("Word '{}' of {} is skipped: {}", word.lemma(), set.sourceLabel(), invalid.getMessage());
                continue;
            }
            if (cache.find(lemma).isEmpty()) {
                cache.put(lemma, word.details(), DictionaryCachePort.Source.WORD_SET, "{}");
            }
            EntryContext context = word.exampleEn() == null || word.exampleEn().isBlank()
                    ? null
                    : new EntryContext(word.exampleEn(), null, set.sourceLabel());
            AddWordResult result =
                    writer.save(userId, lemma, word.details(), context, EntryOrigin.COLLINS_UNIT, null, set.id(), now);
            if (result.created()) {
                added++;
            } else {
                present++;
            }
        }
        return new Enrollment(set.id(), added, present);
    }

    @Override
    public int sync() {
        Instant now = clock.instant();
        List<WordSet> units = content.vocabularyUnits();
        for (WordSet unit : units) {
            sets.upsert(unit, now);
        }
        return units.size();
    }

    private static WordSetView view(WordSet set, Map<UUID, WordSetRepository.Progress> progress) {
        WordSetRepository.Progress found = progress.getOrDefault(set.id(), new WordSetRepository.Progress(0, 0));
        return new WordSetView(set, found.inDictionary(), found.learned());
    }

    private static VocabularyException notFound() {
        return VocabularyException.notFound("The word set does not exist.");
    }
}
