package com.toefly.practice.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.MistakeCategory;
import com.toefly.events.common.Section;
import com.toefly.events.common.TaskType;
import com.toefly.events.practice.AbilityUpdatedV1;
import com.toefly.events.practice.AttemptScoredV1;
import com.toefly.events.practice.AttemptStartedV1;
import com.toefly.events.practice.AttemptSubmittedV1;
import com.toefly.events.practice.ItemResultV1;
import com.toefly.events.practice.TimeTrackedV1;
import com.toefly.practice.application.port.out.PracticeEventPublisherPort;
import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.PinnedItem;
import com.toefly.practice.domain.time.TimeEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps practice facts to the Avro events of the canon and writes them to the outbox (ADR-0005). */
@Component
public class OutboxPracticeEventPublisherAdapter implements PracticeEventPublisherPort {

    static final String ATTEMPT_STARTED = "toefly.practice.attempt-started.v1";
    static final String ATTEMPT_SUBMITTED = "toefly.practice.attempt-submitted.v1";
    static final String ATTEMPT_SCORED = "toefly.practice.attempt-scored.v1";
    static final String ABILITY_UPDATED = "toefly.practice.ability-updated.v1";
    static final String TIME_TRACKED = "toefly.practice.time-tracked.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxPracticeEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public List<String> topics() {
        return List.of(ATTEMPT_STARTED, ATTEMPT_SUBMITTED, ATTEMPT_SCORED, ABILITY_UPDATED, TIME_TRACKED);
    }

    @Override
    public void attemptStarted(AttemptSnapshot attempt, UUID correlationId) {
        AttemptStarted start = attempt.start();
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                ATTEMPT_STARTED,
                start,
                start.occurredAt(),
                correlationId,
                AttemptStartedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(start.occurredAt())
                        .setUserId(start.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setAttemptId(start.attemptId())
                        .setItemSetId(start.itemSetId())
                        .setTaskType(TaskType.valueOf(start.taskType().name()))
                        .setSection(section(start))
                        .setMode(AttemptMode.valueOf(start.mode().name()))
                        .setWeekTaskId(start.weekTaskId())
                        .setExamSessionId(start.examSessionId())
                        .setItemSetVersion(start.itemSetVersion())
                        .setStartedAt(start.occurredAt())
                        .setItemCount(start.items().size())
                        .setEstimatedSeconds(start.estimatedSeconds())
                        .setRetry(start.retry())
                        .build());
    }

    @Override
    public void attemptSubmitted(AttemptSnapshot attempt, int answeredCount, UUID correlationId) {
        AttemptStarted start = attempt.start();
        Instant submittedAt = Objects.requireNonNull(attempt.submittedAt());
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                ATTEMPT_SUBMITTED,
                start,
                submittedAt,
                correlationId,
                AttemptSubmittedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(submittedAt)
                        .setUserId(start.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setAttemptId(start.attemptId())
                        .setItemSetId(start.itemSetId())
                        .setTaskType(TaskType.valueOf(start.taskType().name()))
                        .setSection(section(start))
                        .setMode(AttemptMode.valueOf(start.mode().name()))
                        .setWeekTaskId(start.weekTaskId())
                        .setExamSessionId(start.examSessionId())
                        .setStartedAt(start.occurredAt())
                        .setSubmittedAt(submittedAt)
                        .setActiveSeconds(attempt.activeSecondsAtSubmit())
                        .setAnsweredCount(answeredCount)
                        .setTotalCount(start.items().size())
                        .setAutoSubmitted(
                                attempt.submitReason() == com.toefly.practice.domain.attempt.SubmitReason.TIMEOUT)
                        .build());
    }

    @Override
    public void attemptScored(
            AttemptSnapshot attempt,
            int activeSeconds,
            @Nullable Double thetaBefore,
            boolean unaided,
            List<ChosenOptions> choices,
            UUID correlationId) {
        AttemptStarted start = attempt.start();
        Instant scoredAt = Objects.requireNonNull(attempt.scoredAt());
        Map<UUID, List<String>> chosen = new HashMap<>();
        choices.forEach(choice -> chosen.put(choice.itemId(), choice.optionIds()));
        List<ItemResultV1> items = new ArrayList<>();
        for (PinnedItem item : start.items()) {
            AttemptSnapshot.ItemScore score = attempt.scores().get(item.itemId());
            boolean correct = score != null && score.correct();
            items.add(ItemResultV1.newBuilder()
                    .setItemId(item.itemId())
                    .setOrdinal(item.ordinal())
                    .setScore(score == null ? 0 : score.score())
                    .setMaxScore(score == null ? item.maxScore() : score.maxScore())
                    .setB(item.b())
                    .setCorrect(correct)
                    .setAnswered(score != null && score.answered())
                    .setChosenOptionIds(chosen.getOrDefault(item.itemId(), List.of()))
                    .setMistakeCategory(correct || (score != null && score.pending()) ? null : mistake(start))
                    .build());
        }
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                ATTEMPT_SCORED,
                start,
                scoredAt,
                correlationId,
                AttemptScoredV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(scoredAt)
                        .setUserId(start.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setAttemptId(start.attemptId())
                        .setItemSetId(start.itemSetId())
                        .setTaskType(TaskType.valueOf(start.taskType().name()))
                        .setSection(section(start))
                        .setMode(AttemptMode.valueOf(start.mode().name()))
                        .setWeekTaskId(start.weekTaskId())
                        .setExamSessionId(start.examSessionId())
                        .setScoredAt(scoredAt)
                        .setCorrectCount(attempt.correctCount())
                        .setTotalCount(start.items().size())
                        .setUnansweredCount(attempt.unansweredCount())
                        .setActiveSeconds(activeSeconds)
                        .setEstimatedSeconds(start.estimatedSeconds())
                        .setTimingMultiplier(start.timingMultiplier())
                        .setThetaBefore(thetaBefore)
                        .setUnaided(unaided)
                        .setItems(items)
                        .build());
    }

    @Override
    public void abilityUpdated(UUID attemptId, AbilityEstimate before, AbilityEstimate after, UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        Instant at = after.updatedAt() == null ? Instant.now() : after.updatedAt();
        publisher.publish(new IntegrationEvent(
                eventId,
                ABILITY_UPDATED,
                "AbilityEstimate",
                after.userId() + "/" + after.section(),
                after.userId().toString(),
                "users/" + after.userId() + "/abilities/" + after.section(),
                at,
                after.userId(),
                correlationId,
                null,
                AbilityUpdatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(after.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setSection(Section.valueOf(after.section().name()))
                        .setAttemptId(attemptId)
                        .setThetaBefore(before.theta())
                        .setThetaAfter(after.theta())
                        .setStandardError(after.standardError())
                        .setN(after.n())
                        .build()));
    }

    @Override
    public void timeTracked(TimeEntry entry, int itemCount, @Nullable String section, UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        Instant at = entry.closedAt() == null ? Instant.now() : entry.closedAt();
        publisher.publish(new IntegrationEvent(
                eventId,
                TIME_TRACKED,
                "Attempt",
                entry.contextId().toString(),
                entry.userId().toString(),
                "attempts/" + entry.contextId(),
                at,
                entry.userId(),
                correlationId,
                null,
                TimeTrackedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(entry.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setAttemptId(entry.contextId())
                        .setTaskType(TaskType.valueOf(
                                Objects.requireNonNull(entry.taskType()).name()))
                        .setSection(section == null ? null : Section.valueOf(section))
                        .setWeekSection(entry.weekSection())
                        .setLocalDate(entry.localDate())
                        .setActiveSeconds(entry.activeSeconds())
                        .setItemCount(itemCount)
                        .setSuspicious(entry.suspicious())
                        .build()));
    }

    private void publish(
            UUID eventId,
            String topic,
            AttemptStarted start,
            Instant occurredAt,
            UUID correlationId,
            SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                "Attempt",
                start.attemptId().toString(),
                start.userId().toString(),
                "attempts/" + start.attemptId(),
                occurredAt,
                start.userId(),
                correlationId,
                null,
                payload));
    }

    /** Category of a wrong closed item for the learner model of tutor-agent (06 §6.3). */
    static MistakeCategory mistake(AttemptStarted start) {
        return switch (start.taskType()) {
            case COMPLETE_THE_WORDS -> MistakeCategory.SPELLING;
            case BUILD_A_SENTENCE, GRAMMAR_EXERCISE -> MistakeCategory.GRAMMAR;
            case VOCAB_EXERCISE -> MistakeCategory.VOCABULARY;
            default -> MistakeCategory.COMPREHENSION;
        };
    }

    private static @Nullable Section section(AttemptStarted start) {
        return start.section() == null ? null : Section.valueOf(start.section().name());
    }
}
