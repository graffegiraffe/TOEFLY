package com.toefly.content.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTypeTest {

    @Test
    @DisplayName("ETS norms follow docs/03-domain-model.md section 3.6")
    void norms() {
        assertThat(TaskType.COMPLETE_THE_WORDS.etsNormSeconds(10, 0)).isEqualTo(180);
        assertThat(TaskType.READ_DAILY_LIFE.etsNormSeconds(3, 0)).isEqualTo(180);
        assertThat(TaskType.LISTEN_CONVERSATION.etsNormSeconds(2, 41.2)).isEqualTo(42 + 60);
        assertThat(TaskType.LISTEN_ACADEMIC_TALK.etsNormSeconds(4, 100)).isEqualTo(220);
        assertThat(TaskType.GRAMMAR_EXERCISE.etsNormSeconds(8, 0)).isEqualTo(360);
        assertThat(TaskType.LEGACY_LISTENING.etsNormSeconds(5, 120)).isEqualTo(270);
    }

    @Test
    @DisplayName("estimated minutes are the norm rounded up to half a minute")
    void minutes() {
        assertThat(TaskType.estimatedMinutes(150)).isEqualTo(2.5);
        assertThat(TaskType.estimatedMinutes(181)).isEqualTo(3.5);
    }

    @Test
    @DisplayName("only grammar and vocabulary have no section")
    void sections() {
        assertThat(TaskType.GRAMMAR_EXERCISE.section()).isNull();
        assertThat(TaskType.VOCAB_EXERCISE.section()).isNull();
        assertThat(TaskType.LISTEN_AND_REPEAT.section()).isEqualTo(Section.SPEAKING);
    }
}
