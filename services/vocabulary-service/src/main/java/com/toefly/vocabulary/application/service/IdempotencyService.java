package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.IdempotencyUseCase;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.out.IdempotencyStorePort;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
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
    public Optional<StoredResponse> begin(UUID userId, String key, String operation, String requestHash) {
        if (records.claim(userId, key, operation, requestHash, clock.instant().plus(RETENTION))) {
            return Optional.empty();
        }
        IdempotencyStorePort.Record record = records.find(userId, key).orElseThrow(IdempotencyService::inProgress);
        if (!record.operation().equals(operation) || !record.requestHash().equals(requestHash)) {
            throw VocabularyException.unprocessable(
                    "idempotency-key-reuse", "The Idempotency-Key was used for another request.");
        }
        if (!record.completed() || record.status() == null || record.body() == null) {
            throw inProgress();
        }
        return Optional.of(new StoredResponse(record.status(), record.body()));
    }

    @Override
    public void complete(UUID userId, String key, int status, String body) {
        records.complete(userId, key, status, body);
    }

    @Override
    public void release(UUID userId, String key) {
        records.release(userId, key);
    }

    private static VocabularyException inProgress() {
        return VocabularyException.conflict("request-in-progress", "The same request is still in progress.");
    }
}
