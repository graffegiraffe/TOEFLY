package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AbilityChange;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.in.GetAbilityUseCase;
import com.toefly.practice.application.port.out.AbilityStorePort;
import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.ability.RaschAbilityEstimator;
import com.toefly.practice.domain.ability.Response;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.ExamFormat;
import com.toefly.practice.domain.attempt.ItemOutcome;
import com.toefly.practice.domain.attempt.Section;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Updates theta after a scored attempt (docs/03-domain-model.md §7.4, INV-ABL-01..03) and reads it. */
@Service
public class AbilityService implements GetAbilityUseCase {

    private final AbilityStorePort abilities;
    private final StudentAccess access;

    public AbilityService(AbilityStorePort abilities, StudentAccess access) {
        this.abilities = abilities;
        this.access = access;
    }

    /**
     * Applies the scored items of a TOEFL 2026 attempt to its section; empty when the attempt does not count
     * (legacy, grammar and vocabulary sets, open types) or was counted before.
     */
    Optional<Update> apply(AttemptSnapshot attempt, List<ItemOutcome> outcomes, Instant now) {
        AttemptStarted start = attempt.start();
        Section section = start.taskType().thetaSection();
        if (section == null || start.examFormat() != ExamFormat.TOEFL_2026) {
            return Optional.empty();
        }
        List<Response> responses = new ArrayList<>();
        for (ItemOutcome outcome : outcomes) {
            if (!outcome.pending() && outcome.b() != null && outcome.maxScore() > 0) {
                responses.add(new Response(outcome.b(), outcome.score() / outcome.maxScore()));
            }
        }
        if (responses.isEmpty()) {
            return Optional.empty();
        }
        AbilityEstimate before = abilities
                .find(start.userId(), section)
                .orElseGet(() -> AbilityEstimate.initial(start.userId(), section));
        AbilityEstimate after = RaschAbilityEstimator.update(
                before, responses, start.taskType().independentItems(), start.mode(), start.retry(), now);
        if (before.n() < RaschAbilityEstimator.MAP_THRESHOLD) {
            abilities.addResponses(start.userId(), section, responses);
            if (after.n() >= RaschAbilityEstimator.MAP_THRESHOLD) {
                RaschAbilityEstimator.MapEstimate map =
                        RaschAbilityEstimator.map(abilities.responses(start.userId(), section));
                after = new AbilityEstimate(
                        after.userId(), section, map.theta(), map.info(), after.n(), now, after.version());
            }
        }
        if (!abilities.recordHistory(start.attemptId(), before, after)) {
            return Optional.empty();
        }
        abilities.save(after);
        return Optional.of(new Update(before, after));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbilityEstimate> abilities(Caller caller, @Nullable UUID studentId) {
        return abilities.findAll(access.target(caller, studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbilityEstimate> abilitiesOf(UUID userId) {
        return abilities.findAll(userId);
    }

    @Transactional(readOnly = true)
    Optional<AbilityChange> change(UUID attemptId) {
        return abilities.change(attemptId);
    }

    /** Theta of the section before and after the attempt. */
    record Update(AbilityEstimate before, AbilityEstimate after) {
        AbilityChange change() {
            return new AbilityChange(before.section(), before.theta(), after.theta(), after.standardError());
        }
    }
}
