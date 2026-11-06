package com.toefly.content.domain.report;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A complaint about an item set or one of its items. */
public record ItemReport(
        UUID id,
        UUID itemSetId,
        @Nullable Integer itemOrdinal,
        UUID reporterId,
        ReportReason reason,
        @Nullable String comment,
        ReportStatus status,
        @Nullable UUID resolvedBy,
        Instant createdAt,
        @Nullable Instant resolvedAt) {

    public ItemReport {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(status, "status");
    }

    public static ItemReport open(
            UUID id,
            UUID itemSetId,
            @Nullable Integer ordinal,
            UUID reporter,
            ReportReason reason,
            @Nullable String comment,
            Instant now) {
        return new ItemReport(id, itemSetId, ordinal, reporter, reason, comment, ReportStatus.OPEN, null, now, null);
    }

    public ItemReport close(ReportStatus outcome, UUID admin, Instant now) {
        if (status != ReportStatus.OPEN || outcome == ReportStatus.OPEN) {
            throw new IllegalStateException("only an open report can be resolved or dismissed");
        }
        return new ItemReport(id, itemSetId, itemOrdinal, reporterId, reason, comment, outcome, admin, createdAt, now);
    }
}
