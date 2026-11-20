package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import org.jspecify.annotations.Nullable;

/** Avro enums to the domain; {@code UNKNOWN} and symbols of a newer schema become {@code null}. */
final class Enums {

    private Enums() {}

    static @Nullable TaskType taskType(@Nullable Enum<?> value) {
        return value == null ? null : TaskType.parse(value.name());
    }

    static @Nullable Section section(@Nullable Enum<?> value) {
        return value == null ? null : parse(Section.class, value.name());
    }

    static @Nullable WeekSection weekSection(@Nullable Object value) {
        return value == null ? null : parse(WeekSection.class, value.toString());
    }

    private static <E extends Enum<E>> @Nullable E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
