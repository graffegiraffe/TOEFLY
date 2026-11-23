package com.toefly.tutoragent.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.common.MistakeCategory;
import com.toefly.events.tutoragent.LearnerModelUpdatedV1;
import com.toefly.events.tutoragent.MistakeStatV1;
import com.toefly.events.tutoragent.MistakeStatus;
import com.toefly.events.tutoragent.MistakeTrend;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.LearnerEventPublisherPort;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** learner-model-updated with the top 10 subcategories and no text examples (docs/06 §6.2). */
@Component
public class OutboxLearnerEventPublisherAdapter implements LearnerEventPublisherPort {

    static final String LEARNER_MODEL_UPDATED = "toefly.tutor-agent.learner-model-updated.v1";

    private final IntegrationEventPublisher publisher;
    private final Clock clock;

    public OutboxLearnerEventPublisherAdapter(IntegrationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void learnerModelUpdated(LearnerModel model, Cause cause) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        LearnerModelUpdatedV1 payload = LearnerModelUpdatedV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(now)
                .setUserId(model.userId())
                .setCorrelationId(cause.correlationId())
                .setCausationId(cause.causationId())
                .setVersion(model.version())
                .setUpdatedAt(model.updatedAt() == null ? now : model.updatedAt())
                .setMistakeStats(model.mistakeStats().stream()
                        .limit(LearnerModel.TOP)
                        .map(s -> MistakeStatV1.newBuilder()
                                .setCategory(
                                        MistakeCategory.valueOf(s.category().name()))
                                .setSubcategory(s.subcategory())
                                .setWeight(s.weight())
                                .setErrorRate(s.errorRate())
                                .setPriority(s.priority())
                                .setTrend(MistakeTrend.valueOf(s.trend().name()))
                                .setStatus(MistakeStatus.valueOf(s.status().name()))
                                .setOccurrencesLast7Days(s.occurrencesLast7Days())
                                .setUnitRefs(s.unitRefs())
                                .build())
                        .toList())
                .setBands(model.bands().isEmpty() ? null : model.bands())
                .build();
        publisher.publish(new IntegrationEvent(
                eventId,
                LEARNER_MODEL_UPDATED,
                "LearnerModel",
                model.userId().toString(),
                model.userId().toString(),
                "learner-models/" + model.userId(),
                now,
                model.userId(),
                cause.correlationId(),
                cause.causationId(),
                payload));
    }
}
