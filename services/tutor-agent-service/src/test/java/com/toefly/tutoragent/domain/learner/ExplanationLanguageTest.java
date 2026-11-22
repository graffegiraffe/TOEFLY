package com.toefly.tutoragent.domain.learner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExplanationLanguageTest {

    @Test
    @DisplayName("RU in any case means Russian, everything else English (AC-17.06.1)")
    void readsProfileValues() {
        assertThat(ExplanationLanguage.of("ru")).isEqualTo(ExplanationLanguage.RU);
        assertThat(ExplanationLanguage.of("EN")).isEqualTo(ExplanationLanguage.EN);
        assertThat(ExplanationLanguage.of("de")).isEqualTo(ExplanationLanguage.EN);
    }
}
