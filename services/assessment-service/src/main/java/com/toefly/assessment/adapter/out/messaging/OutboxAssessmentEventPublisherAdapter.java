package com.toefly.assessment.adapter.out.messaging;

import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.SectionEstimate;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.assessment.BandEstimateUpdatedV1;
import com.toefly.events.assessment.CriterionScoreV1;
import com.toefly.events.assessment.EstimateStatus;
import com.toefly.events.assessment.EvaluationRequestedV1;
import com.toefly.events.assessment.MistakeCountV1;
import com.toefly.events.assessment.SectionEstimateV1;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.AttemptMode;
import com.toefly.events.common.MistakeCategory;
import com.toefly.events.common.Section;
import com.toefly.events.common.SubmissionStatus;
import com.toefly.events.common.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

/** Maps assessment facts to the Avro events of the canon and writes them to the outbox (ADR-0005). */
@Component
public class OutboxAssessmentEventPublisherAdapter implements AssessmentEventPublisherPort {

    static final String EVALUATION_REQUESTED = "toefly.assessment.evaluation-requested.v1";
    static final String SUBMISSION_EVALUATED = "toefly.assessment.submission-evaluated.v1";
    static final String BAND_ESTIMATE_UPDATED = "toefly.assessment.band-estimate-updated.v1";

    private final IntegrationEventPublisher publisher;
    private final Clock clock;

    public OutboxAssessmentEventPublisherAdapter(IntegrationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void evaluationRequested(Submission submission, int versionNo, int attemptNo, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        publish(
                eventId,
                EVALUATION_REQUESTED,
                submission,
                now,
                cause,
                EvaluationRequestedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(submission.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSubmissionId(submission.id())
                        .setVersionNo(versionNo)
                        .setTaskType(TaskType.valueOf(submission.taskType().name()))
                        .setRubricId(submission.taskType().rubricId())
                        .setMode(AttemptMode.valueOf(submission.mode().name()))
                        .setAttemptNo(attemptNo)
                        .setRecordingIds(List.of())
                        .setReferenceTexts(List.of())
                        .build());
    }

    @Override
    public void submissionEvaluated(Submission submission, Evaluation evaluation, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = evaluation.createdAt();
        Map<MistakeCategory, Long> mistakes = evaluation.annotations().stream()
                .collect(Collectors.groupingBy(
                        (InlineAnnotation a) ->
                                MistakeCategory.valueOf(a.category().name()),
                        TreeMap::new,
                        Collectors.counting()));
        publish(
                eventId,
                SUBMISSION_EVALUATED,
                submission,
                now,
                cause,
                SubmissionEvaluatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(submission.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSubmissionId(submission.id())
                        .setVersionNo(evaluation.versionNo())
                        .setEvaluationId(evaluation.id())
                        .setItemSetId(submission.itemSetId())
                        .setTaskType(TaskType.valueOf(submission.taskType().name()))
                        .setSection(Section.valueOf(submission.taskType().section()))
                        .setStatus(SubmissionStatus.valueOf(submission
                                .version(evaluation.versionNo())
                                .status()
                                .name()))
                        .setWeekTaskId(submission.weekTaskId())
                        .setExamSessionId(submission.examSessionId())
                        .setInterviewSessionId(null)
                        .setEvaluatedAt(now)
                        .setRubricScore(evaluation.overallScore())
                        .setCriteria(evaluation.criteria().stream()
                                .map(c -> CriterionScoreV1.newBuilder()
                                        .setCriterion(c.criterion())
                                        .setScore(c.level())
                                        .build())
                                .toList())
                        .setMistakes(mistakes.entrySet().stream()
                                .map(e -> MistakeCountV1.newBuilder()
                                        .setCategory(e.getKey())
                                        .setCount(e.getValue().intValue())
                                        .build())
                                .toList())
                        .setWordCount(submission.version(evaluation.versionNo()).wordCount())
                        .setPromptKey(evaluation.promptKey() == null ? "" : evaluation.promptKey())
                        .setPromptVersion(evaluation.promptVersion())
                        .setFinalScore(evaluation.finalScore())
                        .build());
    }

    @Override
    public void bandEstimateUpdated(UUID userId, BandEstimate estimate, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        Function<SectionEstimate, SectionEstimateV1> section = s -> SectionEstimateV1.newBuilder()
                .setSection(Section.valueOf(s.section().name()))
                .setStatus(s.ready() ? EstimateStatus.READY : EstimateStatus.NEED_MORE_DATA)
                .setPointBand(s.point())
                .setLow(s.low())
                .setHigh(s.high())
                .setN(s.n())
                .build();
        BandEstimate.Overall overall = estimate.overall();
        publisher.publish(new IntegrationEvent(
                eventId,
                BAND_ESTIMATE_UPDATED,
                "BandEstimate",
                userId.toString(),
                userId.toString(),
                "users/" + userId + "/band-estimate",
                now,
                userId,
                cause.correlationId(),
                cause.causationId(),
                BandEstimateUpdatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(now)
                        .setUserId(userId)
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSections(estimate.sections().stream().map(section).toList())
                        .setOverallPoint(overall == null ? null : overall.point())
                        .setOverallLow(overall == null ? null : overall.low())
                        .setOverallHigh(overall == null ? null : overall.high())
                        .setAsOf(estimate.asOf())
                        .build()));
    }

    private void publish(
            UUID eventId,
            String topic,
            Submission submission,
            Instant occurredAt,
            Cause cause,
            SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                "Submission",
                submission.id().toString(),
                submission.userId().toString(),
                "submissions/" + submission.id(),
                occurredAt,
                submission.userId(),
                cause.correlationId(),
                cause.causationId(),
                payload));
    }
}
