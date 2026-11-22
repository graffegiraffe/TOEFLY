package com.toefly.billing.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortTypesTest {

    @Test
    void causesAndErrorsKeepTheirData() {
        UUID saga = UUID.randomUUID();

        assertThat(Cause.of(saga).correlationId()).isEqualTo(saga);
        assertThat(Cause.none().causationId()).isNull();
        IllegalStateException root = new IllegalStateException("down");
        assertThat(new ClassroomUnavailableException("classroom", root))
                .hasCause(root)
                .hasMessage("classroom");
        assertThat(new ProviderEvent.Refunded("evt", "charge.refunded", "pi_1").providerPaymentId())
                .isEqualTo("pi_1");
        assertThat(new CheckoutEmulatorPort.Delivery(java.util.Map.of(), new byte[0]).headers())
                .isEmpty();
        assertThat(new SeatRepository.Holding(saga, saga).tutorId()).isEqualTo(saga);
    }
}
