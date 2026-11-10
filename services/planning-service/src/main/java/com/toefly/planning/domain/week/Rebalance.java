package com.toefly.planning.domain.week;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a rebalance changed (docs/03-domain-model.md §10.5). */
public record Rebalance(double lagDays, List<UUID> skipped, List<Move> moves, boolean manual) {

    public Rebalance {
        skipped = List.copyOf(skipped);
        moves = List.copyOf(moves);
    }

    /** A pending task with its new day. */
    public record Move(UUID taskId, LocalDate date) {}
}
