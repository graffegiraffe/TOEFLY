package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.AddWordResult;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.application.port.out.CardRepository;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.VocabularyEventPublisherPort;
import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.EntryContext;
import com.toefly.vocabulary.domain.entry.EntryOrigin;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.entry.WordDetails;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves one word (AC-15.01.2, AC-15.01.3, INV-VOC-01, INV-VOC-02): a known lemma gets the context, a new one an entry,
 * a NEW card and {@code word-added}, all in one transaction.
 */
@Component
class EntryWriter {

    private final EntryRepository entries;
    private final CardRepository cards;
    private final VocabularyEventPublisherPort events;
    private final FsrsScheduler scheduler;
    private final TransactionTemplate tx;

    EntryWriter(
            EntryRepository entries,
            CardRepository cards,
            VocabularyEventPublisherPort events,
            FsrsScheduler scheduler,
            TransactionTemplate tx) {
        this.entries = entries;
        this.cards = cards;
        this.events = events;
        this.scheduler = scheduler;
        this.tx = tx;
    }

    AddWordResult save(
            UUID userId,
            String lemma,
            WordDetails details,
            @Nullable EntryContext context,
            EntryOrigin origin,
            @Nullable UUID itemSetId,
            @Nullable UUID wordSetId,
            Instant now) {
        AddWordResult result =
                tx.execute(status -> attempt(userId, lemma, details, context, origin, itemSetId, wordSetId, now)
                        .orElse(null));
        if (result != null) {
            return result;
        }
        // a concurrent request inserted the lemma first: add the context to its entry
        return tx.execute(status -> attempt(userId, lemma, details, context, origin, itemSetId, wordSetId, now)
                .orElseThrow(() -> new IllegalStateException("lemma " + lemma + " could not be saved")));
    }

    private Optional<AddWordResult> attempt(
            UUID userId,
            String lemma,
            WordDetails details,
            @Nullable EntryContext context,
            EntryOrigin origin,
            @Nullable UUID itemSetId,
            @Nullable UUID wordSetId,
            Instant now) {
        Optional<VocabularyEntry> existing = entries.findByLemmaForUpdate(userId, lemma);
        if (existing.isPresent()) {
            VocabularyEntry entry = existing.get().addContext(context, now);
            if (!existing.get().enriched() && details.complete()) {
                entry = entry.enrich(details, now);
            }
            if (!entry.equals(existing.get())) {
                entry = entries.update(entry);
            }
            ReviewCard card = cards.findByEntry(entry.id()).orElseThrow();
            return Optional.of(new AddWordResult(new EntryRow(entry, card), false));
        }
        VocabularyEntry entry =
                VocabularyEntry.create(UUID.randomUUID(), userId, lemma, details, context, origin, wordSetId, now);
        if (!entries.insert(entry)) {
            return Optional.empty();
        }
        ReviewCard card = ReviewCard.create(
                UUID.randomUUID(), entry.id(), userId, scheduler.parameters().version(), now);
        cards.insert(card);
        events.wordAdded(entry, itemSetId, now, null);
        return Optional.of(new AddWordResult(new EntryRow(entry, card), true));
    }
}
