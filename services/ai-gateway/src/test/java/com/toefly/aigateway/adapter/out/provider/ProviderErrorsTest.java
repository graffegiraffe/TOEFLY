package com.toefly.aigateway.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.application.port.out.ProviderFailure;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderErrorsTest {

    @Test
    @DisplayName("402 (no prepaid credit) pauses the model for an hour instead of failing every call (ADR-0033)")
    void noCreditPausesTheModel() {
        ProviderFailure failure = ProviderErrors.byStatus("OPENROUTER", 402, null, null);
        assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.RATE_LIMITED);
        assertThat(failure.retryAfter()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("other 4xx answers stay rejected, 5xx stay unavailable")
    void otherStatusesKeepTheirKind() {
        assertThat(ProviderErrors.byStatus("OPENROUTER", 400, null, null).kind())
                .isEqualTo(ProviderFailure.Kind.REJECTED);
        assertThat(ProviderErrors.byStatus("OPENROUTER", 503, null, null).kind())
                .isEqualTo(ProviderFailure.Kind.UNAVAILABLE);
    }
}
