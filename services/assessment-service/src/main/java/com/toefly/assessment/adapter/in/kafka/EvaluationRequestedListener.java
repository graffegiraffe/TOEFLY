package com.toefly.assessment.adapter.in.kafka;

import com.toefly.assessment.application.port.in.EvaluateSubmissionUseCase;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.assessment.EvaluationRequestedV1;
import com.toefly.events.common.TaskType;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * The workers of assessment-service (consumer group {@code assessment-evaluation-workers}, docs/04 §3.7): Writing
 * versions are graded here; Speaking waits for speech-service from phase 6.
 */
@Component
public class EvaluationRequestedListener {

    static final String CONSUMER = "EvaluationRequestedListener.writing";
    private static final Set<TaskType> WRITING = Set.of(TaskType.WRITE_EMAIL, TaskType.WRITE_ACADEMIC_DISCUSSION);

    private final ProcessedEventGuard guard;
    private final EvaluateSubmissionUseCase evaluations;

    public EvaluationRequestedListener(ProcessedEventGuard guard, EvaluateSubmissionUseCase evaluations) {
        this.guard = guard;
        this.evaluations = evaluations;
    }

    @ToeflyListener(
            id = "evaluation-requested",
            topics = "toefly.assessment.evaluation-requested.v1",
            groupId = "assessment-evaluation-workers")
    void onRequested(ConsumerRecord<String, EvaluationRequestedV1> record) {
        EvaluationRequestedV1 event = record.value();
        if (event == null || !WRITING.contains(event.getTaskType())) {
            return;
        }
        evaluations.evaluate(
                event.getSubmissionId(),
                event.getVersionNo(),
                new Cause(event.getCorrelationId(), event.getEventId()),
                effect -> guard.runOnce(record, CONSUMER, effect));
    }
}
