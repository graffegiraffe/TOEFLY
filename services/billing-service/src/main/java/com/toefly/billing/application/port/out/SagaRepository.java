package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.SagaStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface SagaRepository {

    /** Inserts once per correlation id (one saga per confirmed payment). */
    boolean insertIfAbsent(PaymentSaga saga);

    void update(PaymentSaga saga);

    Optional<PaymentSaga> findById(UUID id);

    Optional<PaymentSaga> findByCorrelation(UUID correlationId);

    /** One due saga under {@code FOR UPDATE SKIP LOCKED}: one executor per saga instance. */
    Optional<PaymentSaga> lockDue(Instant now);

    Optional<PaymentSaga> lock(UUID id);

    Page page(@Nullable SagaStatus status, @Nullable Instant beforeUpdatedAt, @Nullable UUID beforeId, int limit);

    record Page(List<PaymentSaga> items, boolean more) {}
}
