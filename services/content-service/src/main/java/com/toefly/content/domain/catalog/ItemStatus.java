package com.toefly.content.domain.catalog;

import java.util.EnumSet;
import java.util.Set;

/** Status machine of docs/03-domain-model.md §3.7. */
public enum ItemStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    HIDDEN,
    REJECTED;

    public Set<ItemStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(IN_REVIEW, APPROVED, REJECTED);
            case IN_REVIEW -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(HIDDEN, IN_REVIEW);
            case HIDDEN -> EnumSet.of(APPROVED, REJECTED);
            case REJECTED -> EnumSet.noneOf(ItemStatus.class);
        };
    }

    public boolean canMoveTo(ItemStatus target) {
        return next().contains(target);
    }
}
