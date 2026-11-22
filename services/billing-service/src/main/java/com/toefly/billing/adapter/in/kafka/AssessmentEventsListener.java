package com.toefly.billing.adapter.in.kafka;

import com.toefly.billing.application.port.in.LifecycleUseCase;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import com.toefly.events.common.SubmissionStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * Counts AI checks against the Free limit (docs/02 §1.3 rule 1): each evaluated version is one check; a check inside an
 * exam session (diagnostic) is not counted (rule 2).
 */
@Component
public class AssessmentEventsListener {

    private final ProcessedEventGuard guard;
    private final LifecycleUseCase lifecycle;

    public AssessmentEventsListener(ProcessedEventGuard guard, LifecycleUseCase lifecycle) {
        this.guard = guard;
        this.lifecycle = lifecycle;
    }

    @ToeflyListener(id = "billing-submission-evaluated", topics = "toefly.assessment.submission-evaluated.v1")
    void onEvaluated(ConsumerRecord<String, SubmissionEvaluatedV1> record) {
        SubmissionEvaluatedV1 event = record.value();
        if (event.getStatus() != SubmissionStatus.EVALUATED) {
            return;
        }
        guard.runOnce(
                record,
                "AssessmentEventsListener.evaluated",
                () -> lifecycle.aiCheckEvaluated(
                        event.getUserId(), event.getExamSessionId() != null, event.getEventId()));
    }
}
