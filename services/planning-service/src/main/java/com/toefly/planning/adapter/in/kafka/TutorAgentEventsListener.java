package com.toefly.planning.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.tutoragent.LearnerModelUpdatedV1;
import com.toefly.events.tutoragent.MistakeStatV1;
import com.toefly.events.tutoragent.WeeklyReviewGeneratedV1;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase.ReviewMistake;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase.WeeklyReview;
import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * The learner model and the weekly review of tutor-agent shape the next week (docs/06-ai-agent.md §5.2, docs/12
 * phase 5c decision 1).
 */
@Component
public class TutorAgentEventsListener {

    static final String REVIEW_CONSUMER = "TutorAgentEventsListener.weeklyReview";

    private final ProcessedEventGuard guard;
    private final SyncLearnerUseCase sync;
    private final DraftNextWeekUseCase drafts;

    public TutorAgentEventsListener(ProcessedEventGuard guard, SyncLearnerUseCase sync, DraftNextWeekUseCase drafts) {
        this.guard = guard;
        this.sync = sync;
        this.drafts = drafts;
    }

    /** WeekGenerator calls the model outside the transaction; the write and processed_events commit together. */
    @ToeflyListener(id = "weekly-review-generated", topics = "toefly.tutor-agent.weekly-review-generated.v1")
    void onWeeklyReview(ConsumerRecord<String, WeeklyReviewGeneratedV1> record) {
        WeeklyReviewGeneratedV1 event = record.value();
        if (event == null) {
            return; // tombstone after an erasure
        }
        drafts.weeklyReviewGenerated(
                review(event),
                new Cause(event.getCorrelationId(), event.getEventId()),
                effect -> guard.runOnce(record, REVIEW_CONSUMER, effect));
    }

    static WeeklyReview review(WeeklyReviewGeneratedV1 event) {
        return new WeeklyReview(
                event.getReviewId(),
                event.getUserId(),
                event.getWeekId(),
                event.getWeekNo(),
                event.getLowActivity(),
                event.getTopMistakes().stream()
                        .map(mistake -> new ReviewMistake(
                                code(mistake.getCategory().name(), mistake.getSubcategory()), mistake.getCount()))
                        .toList(),
                event.getNextWeekFocus().stream()
                        .map(focus -> new FocusPoint(focus.getSubcategory(), focus.getWhy()))
                        .toList());
    }

    private static String code(String category, String subcategory) {
        return subcategory.contains(".") ? subcategory : category + "." + subcategory;
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
