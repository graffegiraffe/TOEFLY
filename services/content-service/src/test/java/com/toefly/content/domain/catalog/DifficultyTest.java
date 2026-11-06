package com.toefly.content.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DifficultyTest {

    @Test
    @DisplayName("b = b_cefr + delta(subskill) (docs/03-domain-model.md section 7.5)")
    void seedLabel() {
        assertThat(Difficulty.initialB("B2", "INFERENCE", false)).isEqualTo(0.7);
        assertThat(Difficulty.initialB("B1", "FACTUAL", false)).isEqualTo(-0.5);
        assertThat(Difficulty.initialB("C1", "DETAIL", false)).isEqualTo(1.4);
    }

    @Test
    @DisplayName("legacy and unlabelled sets sit at zero")
    void neutral() {
        assertThat(Difficulty.initialB("C2", "INFERENCE", true)).isZero();
        assertThat(Difficulty.initialB(null, null, false)).isZero();
    }
}
