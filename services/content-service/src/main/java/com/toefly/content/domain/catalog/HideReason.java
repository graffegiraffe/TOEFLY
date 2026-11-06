package com.toefly.content.domain.catalog;

/** Why an ItemSet is hidden; sent as the reason of {@code item-set-hidden}. */
public enum HideReason {
    ADMIN,
    REPORT,
    SEED_REMOVED,
    SOURCE_TAKEDOWN,
    QUALITY
}
