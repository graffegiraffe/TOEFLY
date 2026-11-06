package com.toefly.content.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void createsNotAnnouncedItemWithStrippedTitle() {
        Item item = Item.create(UUID.randomUUID(), UUID.randomUUID(), "  Week 7  ", NOW);

        assertThat(item.title()).isEqualTo("Week 7");
        assertThat(item.announced()).isFalse();
        assertThat(item.markAnnounced().announced()).isTrue();
    }

    @Test
    void rejectsBlankOrLongTitle() {
        assertThatThrownBy(() -> Item.create(UUID.randomUUID(), UUID.randomUUID(), " ", NOW))
                .isInstanceOf(InvalidItemTitleException.class);
        assertThatThrownBy(() -> Item.create(UUID.randomUUID(), UUID.randomUUID(), "x".repeat(201), NOW))
                .isInstanceOf(InvalidItemTitleException.class);
    }
}
