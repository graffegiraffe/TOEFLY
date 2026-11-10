package com.toefly.practice.domain.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.Section;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RaschAbilityEstimatorTest {

    static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");
    static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("docs/03 §7.4 example: 6 of 10 gaps move theta 0.40 -> 0.427, I 5.0 -> 5.62, n 30 -> 40")
    void documentedExample() {
        AbilityEstimate before = new AbilityEstimate(USER, Section.READING, 0.40, 5.0, 30, NOW, 3);
        double[] b = {-0.2, -0.1, 0, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6};
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < b.length; i++) {
            responses.add(new Response(b[i], i < 6 ? 1 : 0));
        }

        AbilityEstimate after =
                RaschAbilityEstimator.update(before, responses, false, AttemptMode.PRACTICE, false, NOW);

        assertThat(after.theta()).isCloseTo(0.427, within(0.001));
        assertThat(after.info()).isCloseTo(5.62, within(0.01));
        assertThat(after.standardError()).isCloseTo(0.42, within(0.01));
        assertThat(after.n()).isEqualTo(40);
        assertThat(after.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("docs/03 §7.3 example: MAP of the 11-item diagnostic gives theta 0.27, I 2.68, SE 0.61")
    void mapExample() {
        double[] b = {-1.5, -1.5, -0.5, -0.5, 0, 0, 0.5, 0.5, 1.0, 1.5, 1.5};
        int[] x = {1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0};
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < b.length; i++) {
            responses.add(new Response(b[i], x[i]));
        }

        RaschAbilityEstimator.MapEstimate map = RaschAbilityEstimator.map(responses);

        assertThat(map.theta()).isCloseTo(0.27, within(0.01));
        assertThat(map.info()).isCloseTo(2.68, within(0.02));
        assertThat(map.standardError()).isCloseTo(0.61, within(0.01));
    }

    @ParameterizedTest(name = "true theta {0}, seed {1}")
    @CsvSource({"-2.0, 1", "-1.0, 2", "-0.3, 3", "0.0, 4", "0.6, 5", "1.2, 6", "1.9, 7", "2.5, 8"})
    @DisplayName("phase 3 DoD: after 500 synthetic answers theta is within 0.3 logit of the true value")
    void convergesToTrueTheta(double trueTheta, long seed) {
        Random random = new Random(seed);
        AbilityEstimate estimate = AbilityEstimate.initial(USER, Section.READING);
        List<Response> seen = new ArrayList<>();
        Instant now = NOW;
        int answered = 0;
        while (answered < 500) {
            // sets of 5 items around the learner's target difficulty, as planning selects them (§7.2)
            List<Response> set = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                double b = Math.max(-4, Math.min(4, estimate.theta() - 0.847 + random.nextGaussian() * 0.6));
                double p = RaschModel.probability(trueTheta, b);
                set.add(new Response(b, random.nextDouble() < p ? 1 : 0));
            }
            now = now.plus(Duration.ofHours(6));
            int before = estimate.n();
            estimate = RaschAbilityEstimator.update(estimate, set, false, AttemptMode.PRACTICE, false, now);
            seen.addAll(set);
            if (before < RaschAbilityEstimator.MAP_THRESHOLD && estimate.n() >= RaschAbilityEstimator.MAP_THRESHOLD) {
                RaschAbilityEstimator.MapEstimate map = RaschAbilityEstimator.map(seen);
                estimate = new AbilityEstimate(USER, Section.READING, map.theta(), map.info(), estimate.n(), now, 0);
            }
            answered += set.size();
        }

        assertThat(Math.abs(estimate.theta() - trueTheta)).isLessThan(0.3);
        assertThat(estimate.n()).isEqualTo(500);
    }

    @Test
    @DisplayName("independent items count with full weight, EXAM moves theta more, a retry moves it less")
    void weights() {
        AbilityEstimate start = new AbilityEstimate(USER, Section.LISTENING, 0, 2, 0, NOW, 0);
        List<Response> right = List.of(new Response(0, 1), new Response(0, 1), new Response(0, 1), new Response(0, 1));

        double shared = RaschAbilityEstimator.update(start, right, false, AttemptMode.PRACTICE, false, NOW)
                .theta();
        double independent = RaschAbilityEstimator.update(start, right, true, AttemptMode.PRACTICE, false, NOW)
                .theta();
        double exam = RaschAbilityEstimator.update(start, right, false, AttemptMode.EXAM, false, NOW)
                .theta();
        double retry = RaschAbilityEstimator.update(start, right, false, AttemptMode.PRACTICE, true, NOW)
                .theta();

        assertThat(independent).isGreaterThan(shared);
        assertThat(exam).isCloseTo(shared * 1.25, within(1e-9));
        assertThat(retry).isCloseTo(shared * 0.5, within(1e-9));
    }

    @Test
    @DisplayName("INV-ABL-01: a step is at most 0.8, theta stays in [-4, 4] and information within its bounds")
    void bounds() {
        AbilityEstimate start = new AbilityEstimate(USER, Section.READING, 3.5, 40, 0, NOW, 0);
        List<Response> right = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            right.add(new Response(3.5, 1));
        }

        AbilityEstimate after = RaschAbilityEstimator.update(start, right, true, AttemptMode.EXAM, false, NOW);

        assertThat(after.theta()).isEqualTo(4.0);
        assertThat(after.info()).isLessThanOrEqualTo(44.4);
        assertThat(RaschAbilityEstimator.update(start, List.of(), true, AttemptMode.EXAM, false, NOW))
                .isSameAs(start);
        assertThatThrownBy(() -> new AbilityEstimate(USER, Section.READING, 4.5, 1, 0, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AbilityEstimate(USER, Section.READING, 0, 0, 0, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("docs/03 §7.4: after more than 14 days without practice the information decays")
    void forgetting() {
        AbilityEstimate old = new AbilityEstimate(USER, Section.READING, 1, 20, 100, NOW, 0);

        assertThat(RaschAbilityEstimator.forget(old, NOW.plus(Duration.ofDays(14))))
                .isEqualTo(20);
        assertThat(RaschAbilityEstimator.forget(old, NOW.plus(Duration.ofDays(74))))
                .isCloseTo(20 * Math.exp(-1), within(1e-9));
        assertThat(RaschAbilityEstimator.forget(AbilityEstimate.initial(USER, Section.READING), NOW))
                .isEqualTo(AbilityEstimate.PRIOR_INFO);
    }

    @Test
    @DisplayName("docs/03 §7.1: P(theta = b) = 0.5 and the information is P(1 - P)")
    void model() {
        assertThat(RaschModel.probability(0.7, 0.7)).isEqualTo(0.5);
        assertThat(RaschModel.information(0.7, 0.7)).isEqualTo(0.25);
    }
}
