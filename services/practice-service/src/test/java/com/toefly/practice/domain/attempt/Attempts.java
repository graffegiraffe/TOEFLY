package com.toefly.practice.domain.attempt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Test data for attempts. */
public final class Attempts {

    public static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    private Attempts() {}

    public static List<PinnedItem> items(int count) {
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(new PinnedItem(UUID.nameUUIDFromBytes(("item-" + i).getBytes()), i, -0.5 + 0.2 * i, 1));
        }
        return items;
    }

    public static AttemptEvent.AttemptStarted started(AttemptMode mode, int itemCount, @Nullable Instant deadline) {
        return new AttemptEvent.AttemptStarted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                TaskType.READ_ACADEMIC_PASSAGE,
                Section.READING,
                ExamFormat.TOEFL_2026,
                mode,
                items(itemCount),
                null,
                mode == AttemptMode.EXAM ? UUID.randomUUID() : null,
                null,
                deadline,
                360,
                1.0,
                "UTC",
                false,
                T0);
    }

    public static Attempt practice(int itemCount) {
        return Attempt.start(started(AttemptMode.PRACTICE, itemCount, null));
    }
}
