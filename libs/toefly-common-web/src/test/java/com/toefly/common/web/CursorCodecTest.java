package com.toefly.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    @Test
    void roundTripsKeyValuesForSameFilters() {
        String cursor = CursorCodec.encode("startedAt:desc|status=OPEN", List.of("2026-11-01T09:30:00Z", "id.1"));

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
        assertThat(CursorCodec.decode(cursor, "startedAt:desc|status=OPEN"))
                .containsExactly("2026-11-01T09:30:00Z", "id.1");
    }

    @Test
    void rejectsCursorFromOtherFiltersOrDamaged() {
        String cursor = CursorCodec.encode("status=OPEN", List.of("a"));

        assertThatThrownBy(() -> CursorCodec.decode(cursor, "status=DONE")).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> CursorCodec.decode("%%%", "status=OPEN")).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void validatesLimit() {
        assertThat(CursorCodec.limit(null)).isEqualTo(20);
        assertThat(CursorCodec.limit(100)).isEqualTo(100);
        assertThatThrownBy(() -> CursorCodec.limit(0)).isInstanceOf(InvalidLimitException.class);
        assertThatThrownBy(() -> CursorCodec.limit(101)).isInstanceOf(InvalidLimitException.class);
    }
}
