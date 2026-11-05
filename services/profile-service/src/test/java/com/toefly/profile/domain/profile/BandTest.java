package com.toefly.profile.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BandTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 6.5, 4.25, 3.1})
    @DisplayName("AC-02.01.1 a band is 1.0 to 6.0 in steps of 0.5")
    void rejectsValuesOutsideTheScale(double value) {
        assertThatThrownBy(() -> Band.of(value)).isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    @DisplayName("AC-02.01.2 the overall target is the mean of sections rounded to 0.5")
    void roundsMeanHalfUp() {
        assertThat(Band.roundHalfMean(List.of(Band.of(4), Band.of(4), Band.of(4.5), Band.of(4.5))))
                .isEqualTo(Band.of(4.5));
        assertThat(Band.roundHalfMean(List.of(Band.of(4), Band.of(4), Band.of(4), Band.of(4.5))))
                .isEqualTo(Band.of(4));
        assertThat(Band.roundHalfMean(List.of(Band.of(5), Band.of(5), Band.of(6), Band.of(6))))
                .isEqualTo(Band.of(5.5));
    }

    @Test
    void meanOfNothingIsAnError() {
        assertThatThrownBy(() -> Band.roundHalfMean(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
