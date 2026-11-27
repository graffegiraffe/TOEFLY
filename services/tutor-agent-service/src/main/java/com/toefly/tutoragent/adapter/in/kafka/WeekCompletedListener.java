package com.toefly.tutoragent.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.planning.WeekCompletedV1;
import com.toefly.tutoragent.application.port.in.ProjectionUseCase;
import com.toefly.tutoragent.application.port.in.WeeklyReviewUseCase;
import com.toefly.tutoragent.application.port.out.ClosedWeek;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * Weekly review on {@code toefly.planning.week-completed.v1} (docs/06 §5.3). The AI call runs outside the
 * processed-event transaction; a redelivery after a crash overwrites the same review (INV-AGT-03).
 */
@Component
public class WeekCompletedListener {

    private final ProcessedEventGuard guard;
    private final WeeklyReviewUseCase reviews;
    private final ProjectionUseCase projections;

    public WeekCompletedListener(
            ProcessedEventGuard guard, WeeklyReviewUseCase reviews, ProjectionUseCase projections) {
        this.guard = guard;
        this.reviews = reviews;
        this.projections = projections;
    }

    @ToeflyListener(id = "week-completed", topics = "toefly.planning.week-completed.v1")
    void onCompleted(ConsumerRecord<String, WeekCompletedV1> record) {
        WeekCompletedV1 e = record.value();
        if (e == null) {
            return;
        }
        ClosedWeek week = new ClosedWeek(
                e.getUserId(),
                e.getWeekId(),
                e.getWeekNo(),
                e.getStartDate(),
                e.getEndDate(),
                e.getClosedAt(),
                e.getTasksTotal(),
                e.getTasksDone(),
                e.getProgressPercent(),
                e.getSections().stream()
                        .map(s -> new ClosedWeek.Section(
                                s.getSection().name(),
                                s.getTasksTotal(),
                                s.getTasksDone(),
                                s.getMinutesPlanned(),
                                s.getMinutesDone()))
                        .toList(),
                e.getCorrelationId(),
                e.getEventId());
        boolean first = guard.runOnce(
                record,
                "WeekCompletedListener",
                () -> projections.weekProgress(
                        e.getUserId(),
                        e.getWeekId(),
                        e.getWeekNo(),
                        (int) Math.round(e.getProgressPercent()),
                        false,
                        e.getClosedAt()));
        if (first) {
            reviews.weekCompleted(week);
        }
    }
}
