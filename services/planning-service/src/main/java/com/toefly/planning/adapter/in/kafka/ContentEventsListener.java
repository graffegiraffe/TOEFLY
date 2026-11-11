package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.content.ItemSetHiddenV1;
import com.toefly.events.content.ItemSetPublishedV1;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.domain.week.TaskType;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** The local catalog of approved sets (docs/12, phase 4, decision 10). */
@Component
public class ContentEventsListener {

    private final ProcessedEventGuard guard;
    private final SyncLearnerUseCase sync;

    public ContentEventsListener(ProcessedEventGuard guard, SyncLearnerUseCase sync) {
        this.guard = guard;
        this.sync = sync;
    }

    @ToeflyListener(id = "item-set-published", topics = "toefly.content.item-set-published.v1")
    void onPublished(ConsumerRecord<String, ItemSetPublishedV1> record) {
        ItemSetPublishedV1 event = record.value();
        if (Arrays.stream(TaskType.values()).noneMatch(type -> type.name().equals(event.getTaskType()))) {
            return; // a type of a later release
        }
        guard.runOnce(
                record,
                "ContentEventsListener.published",
                () -> sync.itemSetPublished(
                        event.getItemSetId(),
                        event.getVersion(),
                        TaskType.valueOf(event.getTaskType()),
                        event.getExamFormat(),
                        event.getDifficultyB(),
                        event.getEstimatedMinutes(),
                        event.getTopic(),
                        event.getOwnerTutorId(),
                        event.getOccurredAt()));
    }

    @ToeflyListener(id = "item-set-hidden", topics = "toefly.content.item-set-hidden.v1")
    void onHidden(ConsumerRecord<String, ItemSetHiddenV1> record) {
        guard.runOnce(
                record,
                "ContentEventsListener.hidden",
                () -> sync.itemSetHidden(
                        record.value().getItemSetId(), record.value().getOccurredAt()));
    }
}
