package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.IdempotencyKeyReuseException;
import com.toefly.planning.application.port.in.IdempotencyUseCase;
import com.toefly.planning.application.port.in.RequestInProgressException;
import com.toefly.planning.application.port.out.IdempotencyStorePort;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Each step commits on its own, so a parallel duplicate sees the claim of the first request. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class IdempotencyService implements IdempotencyUseCase {

    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyStorePort records;
    private final Clock clock;

    public IdempotencyService(IdempotencyStorePort records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Override
    public Optional<StoredResponse> begin(UUID userId, UUID key, String operation, String requestHash) {
        if (records.claim(
                userId, key.toString(), operation, requestHash, clock.instant().plus(RETENTION))) {
            return Optional.empty();
        }
        IdempotencyStorePort.Record record =
                records.find(userId, key.toString()).orElseThrow(RequestInProgressException::new);
        if (!record.operation().equals(operation) || !record.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }
        if (!record.completed() || record.status() == null || record.body() == null) {
            throw new RequestInProgressException();
        }
        return Optional.of(new StoredResponse(record.status(), record.body(), record.resourceUri()));
    }

    @Override
    public void complete(UUID userId, UUID key, int status, String body, @Nullable String resourceUri) {
        records.complete(userId, key.toString(), status, body, resourceUri);
    }

    @Override
    public void release(UUID userId, UUID key) {
        records.release(userId, key.toString());
    }
}
