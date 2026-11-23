package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.AddWordCommand;
import com.toefly.vocabulary.application.port.in.AddWordResult;
import com.toefly.vocabulary.application.port.in.DictionaryUseCase;
import com.toefly.vocabulary.application.port.in.EntryPage;
import com.toefly.vocabulary.application.port.in.EntryQuery;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.in.WordCardView;
import com.toefly.vocabulary.application.port.out.CardRepository;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.EntryContext;
import com.toefly.vocabulary.domain.entry.InvalidTermException;
import com.toefly.vocabulary.domain.entry.Lemma;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.review.DailyLimits;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The personal dictionary. Dictionary data is resolved before the transaction (ai-gateway may take seconds); the
 * entry, its card and {@code word-added} are written in one transaction (outbox).
 */
@Service
public class DictionaryService implements DictionaryUseCase {

    static final int MAX_LIMIT = 100;

    private final EntryRepository entries;
    private final CardRepository cards;
    private final LearnerSettingsRepository settings;
    private final WordData wordData;
    private final EntryWriter writer;
    private final TransactionTemplate tx;
    private final VocabularyPolicy policy;
    private final Clock clock;

    public DictionaryService(
            EntryRepository entries,
            CardRepository cards,
            LearnerSettingsRepository settings,
            WordData wordData,
            EntryWriter writer,
            TransactionTemplate tx,
            VocabularyPolicy policy,
            Clock clock) {
        this.entries = entries;
        this.cards = cards;
        this.settings = settings;
        this.wordData = wordData;
        this.writer = writer;
        this.tx = tx;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public WordCardView lookup(UUID userId, String term, @Nullable String context) {
        WordData.Resolved resolved = wordData.resolve(validTerm(term), context, userId);
        UUID entryId = entries.findByLemma(userId, resolved.lemma())
                .map(VocabularyEntry::id)
                .orElse(null);
        return new WordCardView(
                resolved.lemma(), resolved.details(), resolved.found(), entryId, policy.pronunciationEnabled());
    }

    @Override
    public AddWordResult add(AddWordCommand command) {
        WordData.Resolved resolved = wordData.resolve(validTerm(command.term()), command.context(), command.userId());
        EntryContext context = context(command);
        return writer.save(
                command.userId(),
                resolved.lemma(),
                resolved.details(),
                context,
                command.origin(),
                command.itemSetId(),
                null,
                clock.instant());
    }

    @Override
    public List<AddWordResult> addAll(List<AddWordCommand> commands) {
        List<AddWordResult> results = new ArrayList<>();
        for (AddWordCommand command : commands) {
            results.add(add(command));
        }
        return results;
    }

    @Override
    public EntryRow get(UUID userId, UUID entryId) {
        return tx.execute(status -> row(userId, entryId));
    }

    @Override
    public EntryPage list(UUID userId, EntryQuery query) {
        if (query.limit() < 1 || query.limit() > MAX_LIMIT || query.offset() < 0) {
            throw VocabularyException.invalid("limit must be 1..100 and offset not negative.");
        }
        LearnerDay day = LearnerDay.of(settings, userId, clock.instant());
        return tx.execute(status -> entries.page(userId, query, day.end()));
    }

    @Override
    public EntryRow edit(UUID userId, UUID entryId, int expectedVersion, EntryEdit edit) {
        return tx.execute(status -> {
            EntryRow current = row(userId, entryId);
            if (current.entry().version() != expectedVersion) {
                throw VocabularyException.preconditionFailed();
            }
            Instant now = clock.instant();
            VocabularyEntry entry = entries.update(current.entry().edit(edit.translationRu(), edit.note(), now));
            ReviewCard card = current.card();
            if (edit.suspended() != null && edit.suspended() != card.suspended()) {
                card = cards.update(edit.suspended() ? card.suspend() : card.resume());
            }
            return new EntryRow(entry, card);
        });
    }

    @Override
    public void delete(UUID userId, UUID entryId) {
        tx.executeWithoutResult(status -> {
            row(userId, entryId);
            entries.delete(userId, entryId);
        });
    }

    @Override
    public SettingsView settings(UUID userId) {
        LearnerDay day = LearnerDay.of(settings, userId, clock.instant());
        return new SettingsView(day.limits().newCardsPerDay(), day.zone().getId(), policy.pronunciationEnabled());
    }

    @Override
    public SettingsView setNewCardsPerDay(UUID userId, int newCardsPerDay) {
        if (newCardsPerDay < DailyLimits.MIN_NEW || newCardsPerDay > DailyLimits.MAX_NEW) {
            throw VocabularyException.invalid("New cards per day must be 5..30.");
        }
        settings.saveNewCardsPerDay(userId, newCardsPerDay);
        return settings(userId);
    }

    private EntryRow row(UUID userId, UUID entryId) {
        VocabularyEntry entry = entries.find(userId, entryId)
                .orElseThrow(() -> VocabularyException.notFound("The word is not in your dictionary."));
        ReviewCard card = cards.findByEntry(entry.id())
                .orElseThrow(() -> new IllegalStateException("INV-VOC-02: entry " + entry.id() + " has no card"));
        return new EntryRow(entry, card);
    }

    private static String validTerm(String term) {
        try {
            return Lemma.normalize(term);
        } catch (InvalidTermException invalid) {
            throw VocabularyException.invalid(invalid.getMessage());
        }
    }

    private static @Nullable EntryContext context(AddWordCommand command) {
        if (command.context() == null || command.context().isBlank()) {
            return null;
        }
        String label =
                command.sourceLabel() == null || command.sourceLabel().isBlank() ? "Saved word" : command.sourceLabel();
        return new EntryContext(command.context(), command.itemSetId(), label);
    }
}
