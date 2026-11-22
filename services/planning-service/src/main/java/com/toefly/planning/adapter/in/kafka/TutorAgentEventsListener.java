package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.tutoragent.LearnerModelUpdatedV1;
import com.toefly.events.tutoragent.MistakeStatV1;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** The learner model of tutor-agent feeds the focus of the next week (docs/06-ai-agent.md §5.2). */
@Component
public class TutorAgentEventsListener {

    private final ProcessedEventGuard guard;
    private final SyncLearnerUseCase sync;

    public TutorAgentEventsListener(ProcessedEventGuard guard, SyncLearnerUseCase sync) {
        this.guard = guard;
        this.sync = sync;
    }

    @ToeflyListener(id = "learner-model-updated", topics = "toefly.tutor-agent.learner-model-updated.v1")
    void onLearnerModel(ConsumerRecord<String, LearnerModelUpdatedV1> record) {
        LearnerModelUpdatedV1 event = record.value();
        if (event == null) {
            return; // tombstone after an erasure
        }
        guard.runOnce(record, "TutorAgentEventsListener.learnerModel", () -> sync.learnerModelUpdated(model(event)));
    }

    static LearnerModelView model(LearnerModelUpdatedV1 event) {
        return new LearnerModelView(
                event.getUserId(),
                event.getVersion(),
                event.getUpdatedAt(),
                event.getMistakeStats().stream()
                        .map(TutorAgentEventsListener::stat)
                        .toList(),
                Objects.requireNonNullElse(event.getBands(), Map.of()));
    }

    private static MistakeStat stat(MistakeStatV1 stat) {
        return new MistakeStat(
                stat.getCategory().name(),
                stat.getSubcategory(),
                stat.getPriority(),
                stat.getStatus().name(),
                stat.getOccurrencesLast7Days(),
                stat.getUnitRefs());
    }
}
