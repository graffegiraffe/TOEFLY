package com.toefly.billing.domain.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeatPoolTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    @DisplayName("INV-BIL-02: seats equal the limit; a student holds one seat; no seat beyond the limit")
    void assignWithinLimit() {
        SeatPool pool = SeatPool.empty(UUID.randomUUID()).resize(2, UUID::randomUUID);
        UUID anna = UUID.randomUUID();

        pool = pool.assign(anna, NOW).assign(anna, NOW).assign(UUID.randomUUID(), NOW);

        assertThat(pool.limit()).isEqualTo(2);
        assertThat(pool.used()).isEqualTo(2);
        SeatPool full = pool;
        assertThatThrownBy(() -> full.assign(UUID.randomUUID(), NOW)).isInstanceOf(NoFreeSeatException.class);
        assertThat(pool.release(anna).used()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-27.05.3: TUTOR → TUTOR_PLUS gives 30 seats at once and keeps students")
    void upgradeKeepsStudents() {
        UUID student = UUID.randomUUID();
        SeatPool pool =
                SeatPool.empty(UUID.randomUUID()).resize(10, UUID::randomUUID).assign(student, NOW);

        SeatPool bigger = pool.resize(30, UUID::randomUUID);

        assertThat(bigger.limit()).isEqualTo(30);
        assertThat(bigger.students()).containsExactly(student);
    }

    @Test
    @DisplayName("AC-27.05.4, INV-BIL-06: a downgrade below the connected students is refused")
    void downgradeRefused() {
        SeatPool pool = SeatPool.empty(UUID.randomUUID()).resize(30, UUID::randomUUID);
        for (int i = 0; i < 11; i++) {
            pool = pool.assign(UUID.randomUUID(), NOW);
        }
        SeatPool crowded = pool;

        assertThatThrownBy(() -> crowded.requireFits(10))
                .isInstanceOf(SeatsInUseException.class)
                .hasMessageContaining("Remove students to 10 or fewer");
        SeatPool smaller = crowded.release(crowded.students().get(0)).resize(10, UUID::randomUUID);
        assertThat(smaller.limit()).isEqualTo(10);
        assertThat(smaller.used()).isEqualTo(10);
    }
}
