package com.toefly.profile.domain.privacy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Request to delete the account (US-31.04): the word {@code DELETE}, a sign-in at most five minutes old and one
 * active request per user (INV-PRF-07). Every service deletes its data before the legal deadline.
 */
public record AccountDeletion(
        UUID id, UUID userId, AccountDeletionStatus status, Instant requestedAt, Instant legalDeadlineAt) {

    public static final String CONFIRMATION = "DELETE";
    public static final Duration MAX_AUTH_AGE = Duration.ofMinutes(5);
    public static final Duration LEGAL_DEADLINE = Duration.ofDays(30);

    public static AccountDeletion request(
            UUID userId,
            String confirmation,
            @Nullable Instant authTime,
            @Nullable AccountDeletion active,
            Instant now) {
        if (!CONFIRMATION.equals(confirmation)) {
            throw new DeletionNotConfirmedException();
        }
        if (authTime == null || authTime.plus(MAX_AUTH_AGE).isBefore(now)) {
            throw new ReauthenticationRequiredException();
        }
        if (active != null && active.status() != AccountDeletionStatus.COMPLETED) {
            throw new AccountPendingDeletionException();
        }
        return new AccountDeletion(
                UUID.randomUUID(), userId, AccountDeletionStatus.REQUESTED, now, now.plus(LEGAL_DEADLINE));
    }
}
