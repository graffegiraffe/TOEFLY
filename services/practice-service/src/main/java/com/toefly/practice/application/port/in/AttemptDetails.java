package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.AttemptStatus;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** An attempt with the content the learner sees: questions without keys, answers, timer state. */
public record AttemptDetails(AttemptSnapshot attempt, ItemSetContent content, int activeSeconds) {

    /** Transcripts appear once the attempt is scored (AC-11.01.6). */
    public @Nullable Map<String, Object> visibleStimulus() {
        return attempt.status() == AttemptStatus.SCORED ? content.reviewStimulus() : content.stimulus();
    }
}
