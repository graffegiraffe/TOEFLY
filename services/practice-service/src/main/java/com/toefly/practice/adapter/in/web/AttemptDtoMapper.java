package com.toefly.practice.adapter.in.web;

import com.toefly.practice.adapter.in.web.dto.AbilityChange;
import com.toefly.practice.adapter.in.web.dto.AnswerValue;
import com.toefly.practice.adapter.in.web.dto.AttemptItem;
import com.toefly.practice.adapter.in.web.dto.AttemptReview;
import com.toefly.practice.adapter.in.web.dto.AttemptSummary;
import com.toefly.practice.adapter.in.web.dto.ChoiceAnswer;
import com.toefly.practice.adapter.in.web.dto.Explanation;
import com.toefly.practice.adapter.in.web.dto.ExplanationSource;
import com.toefly.practice.adapter.in.web.dto.ItemReview;
import com.toefly.practice.adapter.in.web.dto.ItemReviewDistractorExplanationsInner;
import com.toefly.practice.adapter.in.web.dto.MappingAnswer;
import com.toefly.practice.adapter.in.web.dto.MappingAnswerPairsInner;
import com.toefly.practice.adapter.in.web.dto.Score;
import com.toefly.practice.adapter.in.web.dto.SequenceAnswer;
import com.toefly.practice.adapter.in.web.dto.TextAnswer;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AnswerFormException;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.PinnedItem;
import com.toefly.practice.domain.attempt.Section;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Domain and read models to the DTOs of practice-service.yaml and back. */
final class AttemptDtoMapper {

    private AttemptDtoMapper() {}

    static com.toefly.practice.adapter.in.web.dto.Attempt attempt(AttemptDetails details) {
        AttemptSnapshot state = details.attempt();
        AttemptStarted start = state.start();
        List<AttemptItem> items = new ArrayList<>();
        for (PinnedItem item : start.items()) {
            Instant answeredAt = state.answeredAt().get(item.itemId());
            Map<String, Object> content = details.content().items().get(item.ordinal());
            items.add(new AttemptItem()
                    .itemId(item.itemId())
                    .ordinal(item.ordinal())
                    .content(content == null ? Map.of() : content)
                    .answer(answer(state.answers().get(item.itemId())))
                    .answeredAt(time(answeredAt)));
        }
        return new com.toefly.practice.adapter.in.web.dto.Attempt()
                .attemptId(start.attemptId())
                .userId(start.userId())
                .itemSetId(start.itemSetId())
                .taskType(com.toefly.practice.adapter.in.web.dto.TaskType.fromValue(
                        start.taskType().name()))
                .section(section(start.section()))
                .mode(com.toefly.practice.adapter.in.web.dto.AttemptMode.fromValue(
                        start.mode().name()))
                .status(status(state.status()))
                .weekTaskId(start.weekTaskId())
                .examSessionId(start.examSessionId())
                .startedAt(time(start.occurredAt()))
                .submittedAt(time(state.submittedAt()))
                .scoredAt(time(state.scoredAt()))
                .deadlineAt(time(start.deadlineAt()))
                .estimatedSeconds(start.estimatedSeconds())
                .activeSeconds(state.submittedAt() == null ? details.activeSeconds() : state.activeSecondsAtSubmit())
                .paused(state.paused())
                .timingMultiplier(com.toefly.practice.adapter.in.web.dto.Attempt.TimingMultiplierEnum.fromValue(
                        new BigDecimal(String.valueOf(start.timingMultiplier()))))
                .stimulus(details.visibleStimulus())
                .items(items)
                .version(state.version());
    }

    static AttemptReview review(AttemptReviewResult result) {
        AttemptSnapshot state = result.attempt();
        AttemptStarted start = state.start();
        List<ItemReview> items = new ArrayList<>();
        for (AttemptReviewResult.ItemReviewResult item : result.items()) {
            items.add(new ItemReview()
                    .itemId(item.itemId())
                    .ordinal(item.ordinal())
                    .correct(item.correct())
                    .userAnswer(answer(item.userAnswer()))
                    .correctAnswer(answer(item.correctAnswer()))
                    .explanation(
                            item.explanation() == null
                                    ? null
                                    : new Explanation().text(item.explanation()).source(ExplanationSource.STATIC))
                    .distractorExplanations(item.distractors().stream()
                            .map(distractor -> new ItemReviewDistractorExplanationsInner()
                                    .optionId(distractor.optionId())
                                    .text(distractor.text())
                                    .source(ExplanationSource.STATIC))
                            .toList()));
        }
        return new AttemptReview()
                .attemptId(start.attemptId())
                .itemSetId(start.itemSetId())
                .taskType(com.toefly.practice.adapter.in.web.dto.TaskType.fromValue(
                        start.taskType().name()))
                .section(section(start.section()))
                .mode(com.toefly.practice.adapter.in.web.dto.AttemptMode.fromValue(
                        start.mode().name()))
                .status(status(state.status()))
                .score(new Score()
                        .correct(state.correctCount())
                        .total(start.items().size())
                        .unanswered(state.unansweredCount()))
                .activeSeconds(result.activeSeconds())
                .estimatedSeconds(result.estimatedSeconds())
                .submittedAt(time(state.submittedAt()))
                .scoredAt(time(state.scoredAt()))
                .explanationPending(result.explanationPending())
                .items(items)
                .audioReplays(result.audioReplays())
                .abilityChange(
                        result.abilityChange() == null
                                ? null
                                : new AbilityChange()
                                        .section(section(result.abilityChange().section()))
                                        .thetaBefore(
                                                decimal(result.abilityChange().thetaBefore()))
                                        .thetaAfter(
                                                decimal(result.abilityChange().thetaAfter()))
                                        .standardError(
                                                decimal(result.abilityChange().standardError())))
                .version(state.version());
    }

    static AttemptSummary summary(AttemptView view) {
        return new AttemptSummary()
                .attemptId(view.attemptId())
                .itemSetId(view.itemSetId())
                .taskType(com.toefly.practice.adapter.in.web.dto.TaskType.fromValue(
                        view.taskType().name()))
                .section(section(view.section()))
                .mode(com.toefly.practice.adapter.in.web.dto.AttemptMode.fromValue(
                        view.mode().name()))
                .status(status(view.status()))
                .weekTaskId(view.weekTaskId())
                .startedAt(time(view.startedAt()))
                .submittedAt(time(view.submittedAt()))
                .score(
                        view.status() == AttemptStatus.SCORED
                                ? new Score()
                                        .correct(view.correctCount())
                                        .total(Math.max(1, view.itemCount()))
                                        .unanswered(view.unansweredCount())
                                : null)
                .activeSeconds(view.activeSeconds());
    }

    static com.toefly.practice.adapter.in.web.dto.AbilityEstimate ability(AbilityEstimate estimate) {
        return new com.toefly.practice.adapter.in.web.dto.AbilityEstimate()
                .section(section(estimate.section()))
                .theta(decimal(estimate.theta()))
                .standardError(decimal(estimate.standardError()))
                .itemsObserved(estimate.n())
                .updatedAt(time(estimate.updatedAt() == null ? Instant.EPOCH : estimate.updatedAt()));
    }

    static @Nullable AnswerValue answer(@Nullable Answer answer) {
        return switch (answer) {
            case null -> null;
            case Answer.Choice choice ->
                new ChoiceAnswer(ChoiceAnswer.TypeEnum.CHOICE, new LinkedHashSet<>(choice.optionIds()));
            case Answer.Text text -> new TextAnswer(TextAnswer.TypeEnum.TEXT, text.text());
            case Answer.Sequence sequence ->
                new SequenceAnswer(SequenceAnswer.TypeEnum.SEQUENCE, new ArrayList<>(sequence.fragmentIds()));
            case Answer.Mapping mapping ->
                new MappingAnswer(
                        MappingAnswer.TypeEnum.MAPPING,
                        mapping.pairs().stream()
                                .map(pair -> new MappingAnswerPairsInner(pair.key(), pair.value()))
                                .toList());
        };
    }

    static Answer answer(AnswerValue value) {
        return switch (value) {
            case ChoiceAnswer choice -> new Answer.Choice(List.copyOf(choice.getOptionIds()));
            case TextAnswer text -> new Answer.Text(text.getText());
            case SequenceAnswer sequence -> new Answer.Sequence(sequence.getFragmentIds());
            case MappingAnswer mapping ->
                new Answer.Mapping(mapping.getPairs().stream()
                        .map(pair -> new Answer.Mapping.Pair(pair.getKey(), pair.getValue()))
                        .toList());
            default -> throw new AnswerFormException("Unknown answer type.");
        };
    }

    static com.toefly.practice.adapter.in.web.dto.AttemptStatus status(AttemptStatus status) {
        return com.toefly.practice.adapter.in.web.dto.AttemptStatus.fromValue(status.name());
    }

    static com.toefly.practice.adapter.in.web.dto.@Nullable Section section(@Nullable Section section) {
        return section == null ? null : com.toefly.practice.adapter.in.web.dto.Section.fromValue(section.name());
    }

    static @Nullable OffsetDateTime time(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
