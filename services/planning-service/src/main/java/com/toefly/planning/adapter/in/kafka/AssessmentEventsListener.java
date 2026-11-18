package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.SubmissionStatus;
import com.toefly.planning.application.port.in.TrackAttemptUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.week.TaskType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * An evaluated first version of a Writing task closes its task of the week (docs/05 §8.5.3, docs/12 phase 5b
 * decision 14); the submission plays the part of the attempt, revisions do not move the week.
 */
@Component
public class AssessmentEventsListener {

    private final ProcessedEventGuard guard;
    private final TrackAttemptUseCase attempts;

    public AssessmentEventsListener(ProcessedEventGuard guard, TrackAttemptUseCase attempts) {
        this.guard = guard;
        this.attempts = attempts;
    }

    @ToeflyListener(id = "submission-evaluated", topics = "toefly.assessment.submission-evaluated.v1")
    void onEvaluated(ConsumerRecord<String, SubmissionEvaluatedV1> record) {
        SubmissionEvaluatedV1 event = record.value();
        if (event == null
                || event.getWeekTaskId() == null
                || event.getVersionNo() != 1
                || event.getTaskType() == com.toefly.events.common.TaskType.UNKNOWN
                || (event.getStatus() != SubmissionStatus.EVALUATED
                        && event.getStatus() != SubmissionStatus.TUTOR_REVIEWED)) {
            return;
        }
        guard.runOnce(
                record,
                "AssessmentEventsListener.evaluated",
                () -> attempts.attemptScored(
                        event.getUserId(),
                        event.getWeekTaskId(),
                        event.getSubmissionId(),
                        TaskType.valueOf(event.getTaskType().name()),
                        0,
                        0,
                        false,
                        event.getEvaluatedAt(),
                        new Cause(event.getCorrelationId(), event.getEventId())));
    }
}
