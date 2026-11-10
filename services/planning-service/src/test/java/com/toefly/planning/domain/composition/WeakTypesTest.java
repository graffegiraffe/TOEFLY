package com.toefly.planning.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.planning.domain.composition.WeakTypes.Accuracy;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeakTypesTest {

    @Test
    @DisplayName("docs/02 §4.0: under 60% correct is weak, the weakest first; a few items say nothing")
    void accuracy() {
        Map<TaskType, Accuracy> accuracy = Map.of(
                TaskType.READ_DAILY_LIFE, new Accuracy(5, 10),
                TaskType.LISTEN_CONVERSATION, new Accuracy(2, 10),
                TaskType.COMPLETE_THE_WORDS, new Accuracy(9, 10),
                TaskType.BUILD_A_SENTENCE, new Accuracy(0, 2));
        assertThat(WeakTypes.detect(accuracy, Map.of(), Map.of()))
                .containsExactly(TaskType.LISTEN_CONVERSATION, TaskType.READ_DAILY_LIFE);
    }

    @Test
    @DisplayName("docs/02 §4.0: a section more than 0.5 band under the target makes its weakest type weak")
    void band() {
        Map<TaskType, Accuracy> accuracy = Map.of(
                TaskType.COMPLETE_THE_WORDS, new Accuracy(8, 10),
                TaskType.READ_ACADEMIC_PASSAGE, new Accuracy(7, 10));
        // θ = 0.47 is band 4.0; the target 5.0 is a full band above
        assertThat(WeakTypes.detect(accuracy, Map.of(WeekSection.READING, 0.47), Map.of("OVERALL", 5.0)))
                .containsExactly(TaskType.READ_ACADEMIC_PASSAGE);
        assertThat(WeakTypes.detect(accuracy, Map.of(WeekSection.READING, 0.47), Map.of("READING", 4.5)))
                .isEmpty();
    }

    @Test
    @DisplayName("docs/03 §8.1: θ maps to the band by the nodes of band-scale-v1")
    void bandScale() {
        assertThat(BandScale.band(-5)).isEqualTo(1.0);
        assertThat(BandScale.band(0.47)).isEqualTo(4.0);
        assertThat(BandScale.band((0.47 + 1.16) / 2)).isEqualTo(4.25);
        assertThat(BandScale.band(5)).isEqualTo(6.0);
    }
}
