package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.api.InternalApi;
import com.toefly.vocabulary.adapter.in.web.dto.InternalAddEntryRequest;
import com.toefly.vocabulary.adapter.in.web.dto.InternalDueCards;
import com.toefly.vocabulary.adapter.in.web.dto.InternalDueCardsItemsInner;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntry;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularySummary;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularySummaryLapsedEntriesInner;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularySummaryRecentEntriesInner;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyUserDataExport;
import com.toefly.vocabulary.application.port.in.AddWordCommand;
import com.toefly.vocabulary.application.port.in.DictionaryUseCase;
import com.toefly.vocabulary.application.port.in.DueView;
import com.toefly.vocabulary.application.port.in.InternalVocabularyUseCase;
import com.toefly.vocabulary.application.port.in.ReviewUseCase;
import com.toefly.vocabulary.domain.entry.EntryOrigin;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Internal API: summary and export (docs/05 §9.3.4), tutor-agent tools (docs/06). Leaf operations. */
@RestController
public class InternalVocabularyController implements InternalApi {

    static final int DUE_ITEMS = 10;
    static final String TUTOR_SOURCE = "Toefly Tutor";

    private final InternalVocabularyUseCase internal;
    private final DictionaryUseCase dictionary;
    private final ReviewUseCase reviews;
    private final Idempotent idempotent;

    public InternalVocabularyController(
            InternalVocabularyUseCase internal,
            DictionaryUseCase dictionary,
            ReviewUseCase reviews,
            Idempotent idempotent) {
        this.internal = internal;
        this.dictionary = dictionary;
        this.reviews = reviews;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<VocabularySummary> getVocabularySummary(UUID userId, Integer recentLimit) {
        InternalVocabularyUseCase.Summary summary = internal.summary(userId, recentLimit == null ? 10 : recentLimit);
        return ResponseEntity.ok(new VocabularySummary()
                .entriesTotal(summary.entriesTotal())
                .dueCount(summary.dueCount())
                .recentEntries(summary.recent().stream()
                        .map(entry -> new VocabularySummaryRecentEntriesInner()
                                .entryId(entry.id())
                                .term(entry.lemma())
                                .addedAt(VocabularyDtoMapper.time(entry.createdAt())))
                        .toList())
                .lapsedEntries(summary.lapsed().stream()
                        .map(lapsed -> new VocabularySummaryLapsedEntriesInner()
                                .entryId(lapsed.entryId())
                                .term(lapsed.lemma())
                                .lapses(lapsed.lapses()))
                        .toList()));
    }

    @Override
    public ResponseEntity<VocabularyUserDataExport> exportVocabularyUserData(UUID userId) {
        InternalVocabularyUseCase.Export export = internal.export(userId);
        return ResponseEntity.ok(new VocabularyUserDataExport()
                .userId(export.userId())
                .timezone(export.timezone())
                .newCardsPerDay(export.newCardsPerDay())
                .entries(export.entries().stream()
                        .map(VocabularyDtoMapper::entry)
                        .toList())
                .reviewLogs(export.logs().stream().map(VocabularyDtoMapper::log).toList()));
    }

    @Override
    public ResponseEntity<VocabularyEntry> addStudentVocabularyEntry(
            UUID userId, String idempotencyKey, InternalAddEntryRequest request) {
        return idempotent.run(
                userId,
                idempotencyKey,
                "addStudentVocabularyEntry",
                request,
                VocabularyEntry.class,
                () -> DictionaryController.saved(dictionary.add(new AddWordCommand(
                        userId,
                        request.getTerm(),
                        request.getContextSentence(),
                        null,
                        TUTOR_SOURCE,
                        EntryOrigin.MANUAL))));
    }

    @Override
    public ResponseEntity<InternalDueCards> getStudentDueCards(UUID userId) {
        DueView due = reviews.due(userId);
        return ResponseEntity.ok(new InternalDueCards()
                .dueCount(due.dueCount())
                .newCount(due.newCount())
                .items(due.cards().stream()
                        .limit(DUE_ITEMS)
                        .map(card -> new InternalDueCardsItemsInner()
                                .cardId(card.card().id())
                                .entryId(card.entry().id())
                                .term(card.entry().lemma())
                                .state(com.toefly.vocabulary.adapter.in.web.dto.CardState.valueOf(
                                        card.card().state().name()))
                                .due(VocabularyDtoMapper.time(
                                        card.card().memory().due())))
                        .toList()));
    }
}
