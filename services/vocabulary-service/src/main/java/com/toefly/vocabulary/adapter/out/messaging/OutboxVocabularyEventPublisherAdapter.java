package com.toefly.vocabulary.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.vocabulary.ReviewCompletedV1;
import com.toefly.events.vocabulary.VocabularyEntryOrigin;
import com.toefly.events.vocabulary.WordAddedV1;
import com.toefly.vocabulary.application.port.out.VocabularyEventPublisherPort;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.review.ReviewSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Vocabulary events through the transactional outbox; the key of every topic is {@code userId}. */
@Component
public class OutboxVocabularyEventPublisherAdapter implements VocabularyEventPublisherPort {

    static final String WORD_ADDED = "toefly.vocabulary.word-added.v1";
    static final String REVIEW_COMPLETED = "toefly.vocabulary.review-completed.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxVocabularyEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public List<String> topics() {
        return List.of(WORD_ADDED, REVIEW_COMPLETED);
    }

    @Override
    public void wordAdded(VocabularyEntry entry, @Nullable UUID itemSetId, Instant at, @Nullable UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                WORD_ADDED,
                "VocabularyEntry",
                entry.id(),
                entry.userId(),
                "vocabulary-entries/" + entry.id(),
                at,
                correlationId,
                WordAddedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(entry.userId())
                        .setCorrelationId(correlationId)
                        .setCausationId(null)
                        .setEntryId(entry.id())
                        .setLemma(entry.lemma())
                        .setOrigin(VocabularyEntryOrigin.valueOf(entry.origin().name()))
                        .setItemSetId(itemSetId)
                        .setWordSetId(entry.wordSetId())
                        .build());
    }

    @Override
    public void reviewCompleted(ReviewSession session, int dueRemaining, int dueTomorrow, Instant at) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                REVIEW_COMPLETED,
                "ReviewSession",
                session.id(),
                session.userId(),
                "review-sessions/" + session.id(),
                at,
                null,
                ReviewCompletedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(session.userId())
                        .setCorrelationId(null)
                        .setCausationId(null)
                        .setSessionId(session.id())
                        .setWeekTaskId(session.weekTaskId())
                        .setLocalDate(session.localDate())
                        .setTimezone(session.timezone())
                        .setReviewed(session.reviewedCardIds().size())
                        .setAgainCount(session.againCount())
                        .setNewIntroduced(session.newIntroduced())
                        .setDueRemaining(dueRemaining)
                        .setDueTomorrow(dueTomorrow)
                        .setActiveSeconds(session.activeSeconds())
                        .build());
    }

    private void publish(
            UUID eventId,
            String topic,
            String aggregateType,
            UUID aggregateId,
            UUID userId,
            String subject,
            Instant at,
            @Nullable UUID correlationId,
            SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                aggregateType,
                aggregateId.toString(),
                userId.toString(),
                subject,
                at,
                userId,
                correlationId,
                null,
                payload));
    }
}
