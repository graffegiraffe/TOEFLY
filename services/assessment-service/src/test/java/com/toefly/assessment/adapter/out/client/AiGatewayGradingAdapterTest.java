package com.toefly.assessment.adapter.out.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiGatewayGradingAdapterTest {

    @Test
    @DisplayName("AC-11.03.3 categories and severities outside the lists map to the closest canonical value")
    void normalisesModelLabels() {
        assertThat(AiGatewayGradingAdapter.category("spelling")).isEqualTo(MistakeCategory.SPELLING);
        assertThat(AiGatewayGradingAdapter.category("PUNCTUATION")).isEqualTo(MistakeCategory.GRAMMAR);
        assertThat(AiGatewayGradingAdapter.category("Word choice")).isEqualTo(MistakeCategory.VOCABULARY);
        assertThat(AiGatewayGradingAdapter.category("ORGANIZATION")).isEqualTo(MistakeCategory.COHERENCE);
        assertThat(AiGatewayGradingAdapter.category("Development")).isEqualTo(MistakeCategory.TASK_RESPONSE);
        assertThat(AiGatewayGradingAdapter.category("TASK_RESPONSE")).isEqualTo(MistakeCategory.TASK_RESPONSE);
        assertThat(AiGatewayGradingAdapter.severity("major")).isEqualTo(Severity.MAJOR);
        assertThat(AiGatewayGradingAdapter.severity("high")).isEqualTo(Severity.MINOR);
        assertThat(AiGatewayGradingAdapter.retryAfter("30")).isEqualTo(Duration.ofSeconds(30));
        assertThat(AiGatewayGradingAdapter.retryAfter("soon")).isNull();
    }
}
