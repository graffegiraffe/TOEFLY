package com.toefly.content.domain.item;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sample aggregate of the template; replace it with the service's own model. */
public record Item(UUID id, UUID ownerId, String title, Instant createdAt, boolean announced) {

    public static final int MAX_TITLE_LENGTH = 200;

    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidItemTitleException(MAX_TITLE_LENGTH);
        }
    }

    public static Item create(UUID id, UUID ownerId, String title, Instant now) {
        return new Item(id, ownerId, title.strip(), now, false);
    }

    public Item markAnnounced() {
        return new Item(id, ownerId, title, createdAt, true);
    }
}
