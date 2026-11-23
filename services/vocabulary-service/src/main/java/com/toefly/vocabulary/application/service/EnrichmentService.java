package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.EnrichmentUseCase;
import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.AiUnavailableException;
import com.toefly.vocabulary.application.port.out.DictionaryCachePort;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.entry.WordDetails;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Enriches words saved without dictionary data (docs/04 §3.12): the shared cache first, then ai-gateway; the run stops
 * at the first AI failure and the word is tried again later, up to {@code maxEnrichAttempts} times.
 */
@Service
public class EnrichmentService implements EnrichmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final EntryRepository entries;
    private final DictionaryCachePort cache;
    private final AiDictionaryPort ai;
    private final VocabularyPolicy policy;
    private final Clock clock;

    public EnrichmentService(
            EntryRepository entries,
            DictionaryCachePort cache,
            AiDictionaryPort ai,
            VocabularyPolicy policy,
            Clock clock) {
        this.entries = entries;
        this.cache = cache;
        this.ai = ai;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public int enrichPending(int limit) {
        int enriched = 0;
        for (VocabularyEntry entry : entries.unenriched(limit, policy.maxEnrichAttempts())) {
            Optional<WordDetails> details = cache.find(entry.lemma());
            if (details.isEmpty()) {
                try {
                    String context = entry.latestContext() == null
                            ? null
                            : entry.latestContext().sentence();
                    AiDictionaryPort.WordCard card = ai.wordCard(entry.lemma(), context, entry.userId());
                    if (card.details().complete()) {
                        cache.put(entry.lemma(), card.details(), DictionaryCachePort.Source.AI, card.aiStamp());
                        details = Optional.of(card.details());
                    }
                } catch (AiUnavailableException unavailable) {
                    entries.countEnrichAttempt(entry.id());
                    log.info("Enrichment paused: {}", unavailable.getMessage());
                    return enriched;
                }
            }
            if (details.isEmpty() || !details.get().complete()) {
                entries.countEnrichAttempt(entry.id());
                continue;
            }
            try {
                entries.update(entry.enrich(details.get(), clock.instant()));
                enriched++;
            } catch (OptimisticLockingFailureException changed) {
                // the learner edited the word meanwhile; the next run picks it up again
            }
        }
        return enriched;
    }
}
