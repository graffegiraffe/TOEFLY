package com.toefly.content.domain.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An ItemSet: a stimulus plus ordered items. The {@code payload} is the JSON document defined by the schema of its
 * task type (docs/03-domain-model.md §4); the domain treats it as an opaque string.
 */
public record ItemSet(
        UUID id,
        @Nullable String seedId,
        int version,
        @Nullable Section section,
        TaskType taskType,
        ExamFormat examFormat,
        @Nullable String cefr,
        String topic,
        @Nullable String domain,
        List<String> skills,
        UUID sourceId,
        @Nullable Integer sourcePage,
        @Nullable String sourceItemNo,
        Provenance provenance,
        ItemStatus status,
        @Nullable HideReason hiddenReason,
        @Nullable UUID ownerTutorId,
        double bMean,
        String bBasis,
        int etsNormSeconds,
        double estimatedMinutes,
        int itemCount,
        String payload,
        String audioState,
        String contentHash,
        String searchText,
        @Nullable Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ItemSet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskType, "taskType");
        skills = List.copyOf(skills);
        if (examFormat != taskType.examFormat()) {
            throw new IllegalArgumentException("examFormat LEGACY is for LEGACY_* task types only (INV-ITM-05)");
        }
        if (itemCount < 1) {
            throw new IllegalArgumentException("an item set needs at least one item");
        }
    }

    /** Whether learners may be given this set (INV-ITM-01). */
    public boolean available() {
        return status == ItemStatus.APPROVED;
    }

    public boolean legacy() {
        return examFormat == ExamFormat.LEGACY;
    }

    public ItemSet approve(Instant now) {
        return moveTo(ItemStatus.APPROVED, null, now, publishedAt == null ? now : publishedAt);
    }

    public ItemSet reject(Instant now) {
        return moveTo(ItemStatus.REJECTED, null, now, publishedAt);
    }

    public ItemSet sendToReview(Instant now) {
        return moveTo(ItemStatus.IN_REVIEW, null, now, publishedAt);
    }

    public ItemSet hide(HideReason reason, Instant now) {
        return moveTo(ItemStatus.HIDDEN, Objects.requireNonNull(reason, "reason"), now, publishedAt);
    }

    private ItemSet moveTo(ItemStatus target, @Nullable HideReason reason, Instant now, @Nullable Instant published) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStatusTransitionException(status, target);
        }
        return new ItemSet(
                id, seedId, version, section, taskType, examFormat, cefr, topic, domain, skills, sourceId, sourcePage,
                sourceItemNo, provenance, target, reason, ownerTutorId, bMean, bBasis, etsNormSeconds,
                estimatedMinutes, itemCount, payload, audioState, contentHash, searchText, published, createdAt, now);
    }
}
