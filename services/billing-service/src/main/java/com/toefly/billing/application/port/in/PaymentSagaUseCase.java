package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.SagaStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface PaymentSagaUseCase {

    /** Runs due steps of all sagas; returns the number of steps run. */
    int runDue();

    /** {@code POST /admin/sagas/{id}/retry}. */
    PaymentSaga retry(UUID sagaId);

    SagaPage page(@Nullable SagaStatus status, @Nullable Instant beforeUpdatedAt, @Nullable UUID beforeId, int limit);

    record SagaPage(List<PaymentSaga> items, boolean more) {}
}
