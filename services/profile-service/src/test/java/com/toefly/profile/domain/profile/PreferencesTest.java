package com.toefly.profile.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreferencesTest {

    @Test
    @DisplayName("AC-02.02.1 60 minutes and 19:00 are the defaults")
    void defaults() {
        Preferences preferences = Preferences.defaults(ZoneId.of("Europe/Minsk"));

        assertThat(preferences.minutesPerDay()).isEqualTo(60);
        assertThat(preferences.preferredStudyTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(preferences.explanationLanguage()).isEqualTo(ExplanationLanguage.EN);
    }

    @Test
    @DisplayName("AC-02.02.1 the daily time offers 15 to 180 minutes")
    void minuteOptions() {
        assertThat(Preferences.MINUTE_OPTIONS).containsExactly(15, 30, 45, 60, 90, 120, 180);
        Preferences.MINUTE_OPTIONS.forEach(minutes -> assertThat(Preferences.defaults(ZoneId.of("UTC"))
                        .withSchedule(minutes, LocalTime.NOON)
                        .minutesPerDay())
                .isEqualTo(minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 181, 47})
    void rejectsMinutesOutsideTheRange(int minutes) {
        assertThatThrownBy(() -> Preferences.defaults(ZoneId.of("UTC")).withSchedule(minutes, LocalTime.NOON))
                .isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    @DisplayName("AC-02.03.1 the time zone is an IANA identifier")
    void zoneMustBeIana() {
        assertThat(Preferences.zone("Europe/Minsk")).isEqualTo(ZoneId.of("Europe/Minsk"));
        assertThatThrownBy(() -> Preferences.zone("Mars/Olympus")).isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    @DisplayName("AC-02.03.2 explanations are in English or Russian")
    void explanationLanguages() {
        assertThat(ExplanationLanguage.ofCode("ru")).isEqualTo(ExplanationLanguage.RU);
        assertThatThrownBy(() -> ExplanationLanguage.ofCode("de")).isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    void studyTimeDropsSeconds() {
        assertThat(new Preferences(ZoneId.of("UTC"), ExplanationLanguage.EN, 30, LocalTime.of(7, 30, 15))
                        .preferredStudyTime())
                .isEqualTo(LocalTime.of(7, 30));
    }
}
