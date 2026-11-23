package com.toefly.tutoragent.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.tutoragent.ReengagementMessageCreatedV1;
import com.toefly.events.tutoragent.ReengagementSituation;
import com.toefly.tutoragent.application.port.out.ReengagementPublisherPort;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** reengagement-message-created (docs/06 §5.10). */
@Component
public class OutboxReengagementPublisherAdapter implements ReengagementPublisherPort {

    static final String TOPIC = "toefly.tutor-agent.reengagement-message-created.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxReengagementPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void messageCreated(
            UUID messageId,
            UUID userId,
            Situation situation,
            ComeBackMessage message,
            ExplanationLanguage language,
            boolean aiFallback,
            Instant at) {
        UUID eventId = UUID.randomUUID();
        publisher.publish(new IntegrationEvent(
                eventId,
                TOPIC,
                "ReengagementMessage",
                messageId.toString(),
                userId.toString(),
                "reengagement-messages/" + messageId,
                at,
                userId,
                null,
                null,
                ReengagementMessageCreatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(userId)
                        .setCorrelationId(null)
                        .setCausationId(null)
                        .setMessageId(messageId)
                        .setSituation(ReengagementSituation.valueOf(situation.name()))
                        .setTitle(message.title())
                        .setBody(message.body())
                        .setCtaLabel(message.ctaLabel())
                        .setDeepLink(message.deepLink())
                        .setLanguage(language.name())
                        .setAiFallback(aiFallback)
                        .build()));
    }
}
