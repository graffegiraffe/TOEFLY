package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.PinnedItem;
import java.time.Instant;

/** Projects the state of an attempt into its attempt_view row; the same function serves inline and rebuild. */
final class AttemptProjector {

    private AttemptProjector() {}

    static AttemptView project(AttemptSnapshot state, int activeSeconds, Instant lastActivityAt) {
        AttemptStarted start = state.start();
        double maxScore =
                start.items().stream().mapToDouble(PinnedItem::maxScore).sum();
        return new AttemptView(
                start.attemptId(),
                start.userId(),
                start.itemSetId(),
                start.itemSetVersion(),
                start.taskType(),
                start.section(),
                start.mode(),
                state.status(),
                start.weekTaskId(),
                start.examSessionId(),
                start.occurredAt(),
                state.submittedAt(),
                state.scoredAt(),
                state.rawScore(),
                maxScore,
                start.items().size(),
                state.correctCount(),
                state.unansweredCount(),
                state.submittedAt() == null ? activeSeconds : state.activeSecondsAtSubmit(),
                start.estimatedSeconds(),
                state.answers(),
                lastActivityAt,
                state.version());
    }
}
