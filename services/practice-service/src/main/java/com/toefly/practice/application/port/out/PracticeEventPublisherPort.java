package com.toefly.practice.application.port.out;

import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.time.TimeEntry;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Integration events of practice-service, written to the outbox in the caller's transaction. */
public interface PracticeEventPublisherPort {

    void attemptStarted(AttemptSnapshot attempt, UUID correlationId);

    void attemptSubmitted(AttemptSnapshot attempt, int answeredCount, UUID correlationId);

    void attemptScored(
            AttemptSnapshot attempt,
            int activeSeconds,
            @Nullable Double thetaBefore,
            boolean unaided,
            List<ChosenOptions> choices,
            UUID correlationId);

    void abilityUpdated(UUID attemptId, AbilityEstimate before, AbilityEstimate after, UUID correlationId);

    void timeTracked(TimeEntry entry, int itemCount, @Nullable String section, UUID correlationId);

    /** Topics this publisher writes, for the contract coverage check. */
    List<String> topics();

    /** Options chosen on an item, for distractor analysis (AttemptScoredV1.items[].chosenOptionIds). */
    record ChosenOptions(UUID itemId, List<String> optionIds) {}
}
