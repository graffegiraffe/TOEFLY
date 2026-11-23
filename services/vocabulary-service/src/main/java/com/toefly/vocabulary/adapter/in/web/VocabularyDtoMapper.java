package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.dto.CardSummary;
import com.toefly.vocabulary.adapter.in.web.dto.EntryContext;
import com.toefly.vocabulary.adapter.in.web.dto.ReviewIntervals;
import com.toefly.vocabulary.adapter.in.web.dto.WordData;
import com.toefly.vocabulary.adapter.in.web.dto.WordSetSummary;
import com.toefly.vocabulary.adapter.in.web.dto.WordSetWord;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.application.port.in.ReviewCardView;
import com.toefly.vocabulary.application.port.in.SessionView;
import com.toefly.vocabulary.application.port.in.WordSetView;
import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.card.ReviewLog;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.entry.WordDetails;
import com.toefly.vocabulary.domain.fsrs.Rating;
import com.toefly.vocabulary.domain.review.ReviewSession;
import com.toefly.vocabulary.domain.review.SessionStatus;
import com.toefly.vocabulary.domain.wordset.WordSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/** Domain and use-case views to the DTOs of vocabulary-service.yaml. */
final class VocabularyDtoMapper {

    private VocabularyDtoMapper() {}

    static com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntry entry(EntryRow row) {
        VocabularyEntry entry = row.entry();
        return new com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntry()
                .id(entry.id())
                .lemma(entry.lemma())
                .data(data(entry.details()))
                .note(entry.note())
                .contexts(entry.contexts().stream()
                        .map(VocabularyDtoMapper::context)
                        .toList())
                .origin(com.toefly.vocabulary.adapter.in.web.dto.EntryOrigin.valueOf(
                        entry.origin().name()))
                .wordSetId(entry.wordSetId())
                .enriched(entry.enriched())
                .status(status(row.card()))
                .card(card(row.card()))
                .createdAt(time(entry.createdAt()))
                .updatedAt(time(entry.updatedAt()))
                .version(entry.version());
    }

    static com.toefly.vocabulary.adapter.in.web.dto.ReviewCard reviewCard(ReviewCardView view) {
        VocabularyEntry entry = view.entry();
        String sentence = entry.latestContext() != null
                ? entry.latestContext().sentence()
                : entry.details().exampleEn();
        return new com.toefly.vocabulary.adapter.in.web.dto.ReviewCard()
                .id(view.card().id())
                .entryId(entry.id())
                .lemma(entry.lemma())
                .data(data(entry.details()))
                .contextSentence(sentence)
                .format(com.toefly.vocabulary.adapter.in.web.dto.CardFormat.valueOf(
                        view.format().name()))
                .status(status(view.card()))
                .card(card(view.card()))
                .intervals(new ReviewIntervals()
                        .again(seconds(view.intervals().get(Rating.AGAIN)))
                        .hard(seconds(view.intervals().get(Rating.HARD)))
                        .good(seconds(view.intervals().get(Rating.GOOD)))
                        .easy(seconds(view.intervals().get(Rating.EASY))));
    }

    static com.toefly.vocabulary.adapter.in.web.dto.ReviewSession session(SessionView view) {
        ReviewSession session = view.session();
        boolean completed = session.status() == SessionStatus.COMPLETED;
        return new com.toefly.vocabulary.adapter.in.web.dto.ReviewSession()
                .id(session.id())
                .weekTaskId(session.weekTaskId())
                .status(com.toefly.vocabulary.adapter.in.web.dto.ReviewSession.StatusEnum.valueOf(
                        session.status().name()))
                ._localDate(session.localDate())
                .timezone(session.timezone())
                .planned(session.plannedCardIds().size())
                .reviewed(session.reviewedCardIds().size())
                .againCount(session.againCount())
                .newIntroduced(session.newIntroduced())
                .activeSeconds(session.activeSeconds())
                .startedAt(time(session.startedAt()))
                .completedAt(session.completedAt() == null ? null : time(session.completedAt()))
                .dueRemaining(completed ? view.dueRemaining() : null)
                .dueTomorrow(completed ? view.dueTomorrow() : null)
                .nextDueInSeconds(completed ? view.nextDueInSeconds() : null)
                .cards(view.cards().stream()
                        .map(VocabularyDtoMapper::reviewCard)
                        .toList());
    }

    static WordSetSummary wordSetSummary(WordSetView view) {
        WordSet set = view.set();
        return new WordSetSummary()
                .id(set.id())
                .unitNo(set.unitNo())
                .theme(WordSetSummary.ThemeEnum.fromValue(set.theme()))
                .title(set.title())
                .wordsTotal(set.words().size())
                .inDictionary(view.inDictionary())
                .learned(view.learned())
                .sourceLabel(set.sourceLabel());
    }

    static com.toefly.vocabulary.adapter.in.web.dto.WordSet wordSet(WordSetView view) {
        WordSet set = view.set();
        return new com.toefly.vocabulary.adapter.in.web.dto.WordSet()
                .id(set.id())
                .unitNo(set.unitNo())
                .theme(com.toefly.vocabulary.adapter.in.web.dto.WordSet.ThemeEnum.fromValue(set.theme()))
                .title(set.title())
                .wordsTotal(set.words().size())
                .inDictionary(view.inDictionary())
                .learned(view.learned())
                .sourceLabel(set.sourceLabel())
                .words(set.words().stream()
                        .map(word -> new WordSetWord()
                                .lemma(word.lemma())
                                .partOfSpeech(word.partOfSpeech())
                                .definitionEn(word.definitionEn())
                                .exampleEn(word.exampleEn())
                                .ipa(word.ipa()))
                        .toList());
    }

    static com.toefly.vocabulary.adapter.in.web.dto.ReviewLogExport log(ReviewLog log) {
        return new com.toefly.vocabulary.adapter.in.web.dto.ReviewLogExport()
                .cardId(log.cardId())
                .rating(com.toefly.vocabulary.adapter.in.web.dto.Rating.valueOf(
                        log.rating().name()))
                .stateBefore(com.toefly.vocabulary.adapter.in.web.dto.CardState.valueOf(
                        log.stateBefore().name()))
                .stateAfter(com.toefly.vocabulary.adapter.in.web.dto.CardState.valueOf(
                        log.stateAfter().name()))
                .stabilityAfter(decimal(log.stabilityAfter()))
                .difficultyAfter(decimal(log.difficultyAfter()))
                .scheduledDays(decimal(log.scheduledDays()))
                .reviewedAt(time(log.reviewedAt()))
                .durationMs(log.durationMs());
    }

    static WordData data(WordDetails details) {
        return new WordData()
                .partOfSpeech(details.partOfSpeech())
                .definitionEn(details.definitionEn())
                .exampleEn(details.exampleEn())
                .translationRu(details.translationRu())
                .ipa(details.ipa());
    }

    static CardSummary card(ReviewCard card) {
        return new CardSummary()
                .id(card.id())
                .state(com.toefly.vocabulary.adapter.in.web.dto.CardState.valueOf(
                        card.state().name()))
                .due(time(card.memory().due()))
                .stability(decimal(card.memory().stability()))
                .difficulty(decimal(card.memory().difficulty()))
                .reps(card.memory().reps())
                .lapses(card.memory().lapses())
                .lastReviewAt(
                        card.memory().lastReviewAt() == null
                                ? null
                                : time(card.memory().lastReviewAt()))
                .suspended(card.suspended());
    }

    static com.toefly.vocabulary.adapter.in.web.dto.VocabularyStatus status(ReviewCard card) {
        return com.toefly.vocabulary.adapter.in.web.dto.VocabularyStatus.valueOf(
                card.status().name());
    }

    static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    static String etag(int version) {
        return "\"" + version + "\"";
    }

    private static EntryContext context(com.toefly.vocabulary.domain.entry.EntryContext context) {
        return new EntryContext()
                .sentence(context.sentence())
                .itemSetId(context.itemSetId())
                .sourceLabel(context.sourceLabel());
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static long seconds(@Nullable Duration duration) {
        return duration == null ? 0 : duration.toSeconds();
    }
}
