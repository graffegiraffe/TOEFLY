package com.toefly.practice.domain.scoring;

import java.util.Map;

/** Keys and static explanations of a pinned item set by item ordinal. */
public record ItemSetKeys(Map<Integer, ItemKey> keys, Map<Integer, ItemExplanation> explanations) {

    public ItemSetKeys {
        keys = Map.copyOf(keys);
        explanations = Map.copyOf(explanations);
    }
}
