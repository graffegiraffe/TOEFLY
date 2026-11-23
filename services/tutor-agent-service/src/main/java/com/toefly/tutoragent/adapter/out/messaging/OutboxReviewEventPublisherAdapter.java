package com.toefly.tutoragent.adapter.out.messaging;

import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.common.MistakeCategory;
import com.toefly.events.tutoragent.FocusItemV1;
import com.toefly.events.tutoragent.ReviewMistakeV1;
import com.toefly.events.tutoragent.WeeklyReviewGeneratedV1;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.ReviewEventPublisherPort;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** weekly-review-generated: planning-service builds the next week from its focus (docs/06 §5.2). */
@Component
public class OutboxReviewEventPublisherAdapter implements ReviewEventPublisherPort {

    static final String WEEKLY_REVIEW_GENERATED = "toefly.tutor-agent.weekly-review-generated.v1";

    private final IntegrationEventPublisher publisher;

    public OutboxReviewEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void weeklyReviewGenerated(WeeklyReview review, Cause cause) {
        UUID eventId = UUID.randomUUID();
        WeeklyReviewGeneratedV1 payload = WeeklyReviewGeneratedV1.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(review.generatedAt())
                .setUserId(review.userId())
                .setCorrelationId(cause.correlationId())
                .setCausationId(cause.causationId())
                .setReviewId(review.id())
                .setWeekId(review.weekId())
                .setWeekNo(review.weekNo())
                .setGeneratedAt(review.generatedAt())
                .setLanguage(review.language().name())
                .setAiFallback(review.aiFallback())
                .setLowActivity(review.lowActivity())
                .setTopMistakes(review.text().topMistakes().stream()
                        .map(m -> ReviewMistakeV1.newBuilder()
                                .setCategory(MistakeCategory.valueOf(
                                        com.toefly.tutoragent.domain.learner.MistakeCategory.ofSubcategory(
                                                        m.subcategory())
                                                .name()))
                                .setSubcategory(m.subcategory())
                                .setCount(m.count())
                                .build())
                        .toList())
                .setNextWeekFocus(review.text().nextWeekFocus().stream()
                        .map(f -> FocusItemV1.newBuilder()
                                .setSubcategory(f.subcategory())
                                .setWhy(f.why())
                                .build())
                        .toList())
                .build();
        publisher.publish(new IntegrationEvent(
                eventId,
                WEEKLY_REVIEW_GENERATED,
                "WeeklyReview",
                review.id().toString(),
                review.userId().toString(),
                "weekly-reviews/" + review.id(),
                review.generatedAt(),
                review.userId(),
                cause.correlationId(),
                cause.causationId(),
                payload));
    }
}
