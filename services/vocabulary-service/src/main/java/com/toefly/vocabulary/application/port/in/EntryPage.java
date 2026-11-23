package com.toefly.vocabulary.application.port.in;

import java.util.List;

/** One page of the dictionary and the total of the filter. */
public record EntryPage(List<EntryRow> items, long total, boolean hasMore) {

    public EntryPage {
        items = List.copyOf(items);
    }
}
