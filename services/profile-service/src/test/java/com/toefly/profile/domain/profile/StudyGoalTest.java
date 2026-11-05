package com.toefly.profile.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyGoalTest {

    @Test
    @DisplayName("AC-02.01.2 section targets define the read-only overall target")
    void perSectionComputesOverall() {
        StudyGoal goal = StudyGoal.perSection(Band.of(5), Band.of(5), Band.of(4.5), Band.of(4));

        assertThat(goal.overallTarget()).isEqualTo(Band.of(4.5));
        assertThat(goal.hasSectionTargets()).isTrue();
    }

    @Test
    void overallOnlyHasNoSections() {
        assertThat(StudyGoal.overall(Band.of(5)).hasSectionTargets()).isFalse();
    }

    @Test
    void rejectsOverallThatDisagreesWithSections() {
        assertThatThrownBy(() -> new StudyGoal(Band.of(6), Band.of(4), Band.of(4), Band.of(4), Band.of(4)))
                .isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    void rejectsPartialSections() {
        assertThatThrownBy(() -> new StudyGoal(Band.of(4), Band.of(4), null, null, null))
                .isInstanceOf(InvalidProfileValueException.class);
    }
}
