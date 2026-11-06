package com.toefly.content.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemSetTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private static ItemSet set(TaskType type, ItemStatus status) {
        return new ItemSet(
                UUID.randomUUID(),
                "x/1",
                1,
                type.section(),
                type,
                type.examFormat(),
                "B2",
                "topic",
                null,
                List.of("reading.detail"),
                UUID.randomUUID(),
                1,
                "1",
                Provenance.EXTRACTED,
                status,
                null,
                null,
                0.4,
                "SEED_LABEL",
                360,
                6.0,
                5,
                "{}",
                "UNCHECKED",
                "h".repeat(64),
                "",
                null,
                NOW,
                NOW);
    }

    @Test
    @DisplayName("INV-ITM-02: approving sets the publication time once")
    void approvePublishes() {
        ItemSet approved =
                set(TaskType.READ_ACADEMIC_PASSAGE, ItemStatus.IN_REVIEW).approve(NOW);
        assertThat(approved.available()).isTrue();
        assertThat(approved.publishedAt()).isEqualTo(NOW);
        assertThat(approved.hide(HideReason.ADMIN, NOW.plusSeconds(5))
                        .approve(NOW.plusSeconds(9))
                        .publishedAt())
                .isEqualTo(NOW);
    }

    @Test
    @DisplayName("INV-ITM-01: a hidden set is not available to learners")
    void hiddenIsUnavailable() {
        ItemSet hidden =
                set(TaskType.READ_ACADEMIC_PASSAGE, ItemStatus.APPROVED).hide(HideReason.REPORT, NOW);
        assertThat(hidden.available()).isFalse();
        assertThat(hidden.hiddenReason()).isEqualTo(HideReason.REPORT);
    }

    @Test
    @DisplayName("REJECTED is final and DRAFT cannot be hidden")
    void illegalTransitions() {
        assertThatThrownBy(
                        () -> set(TaskType.READ_DAILY_LIFE, ItemStatus.REJECTED).approve(NOW))
                .isInstanceOf(IllegalStatusTransitionException.class);
        assertThatThrownBy(() -> set(TaskType.READ_DAILY_LIFE, ItemStatus.DRAFT).hide(HideReason.ADMIN, NOW))
                .isInstanceOf(IllegalStatusTransitionException.class);
    }

    @Test
    @DisplayName("INV-ITM-05: LEGACY format belongs to legacy task types only")
    void legacyInvariant() {
        assertThatThrownBy(() -> new ItemSet(
                        UUID.randomUUID(),
                        null,
                        1,
                        Section.READING,
                        TaskType.READ_DAILY_LIFE,
                        ExamFormat.LEGACY,
                        null,
                        "t",
                        null,
                        List.of(),
                        UUID.randomUUID(),
                        null,
                        null,
                        Provenance.EXTRACTED,
                        ItemStatus.APPROVED,
                        null,
                        null,
                        0,
                        "SEED_LABEL",
                        60,
                        1,
                        1,
                        "{}",
                        "UNCHECKED",
                        "h",
                        "",
                        null,
                        NOW,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TaskType.LEGACY_READING.examFormat()).isEqualTo(ExamFormat.LEGACY);
    }
}
