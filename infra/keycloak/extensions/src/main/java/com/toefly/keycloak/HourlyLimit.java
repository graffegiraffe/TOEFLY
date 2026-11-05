package com.toefly.keycloak;

import java.util.Map;

/**
 * Counter of actions per user in a fixed one-hour window, kept in Keycloak's single-use object store so that it is
 * shared by every Keycloak node.
 */
final class HourlyLimit {

    static final long WINDOW_SECONDS = 3600;
    private static final String COUNT = "count";

    /** Store access, a small seam over {@code SingleUseObjectProvider} for tests. */
    interface Store {
        Map<String, String> get(String key);

        boolean putIfAbsent(String key, long lifespanSeconds);

        boolean replace(String key, Map<String, String> notes);
    }

    private final Store store;
    private final int limit;

    HourlyLimit(Store store, int limit) {
        this.store = store;
        this.limit = limit;
    }

    /** Counts one more action; false when the limit of the current window is already used up. */
    boolean tryAcquire(String key) {
        if (store.putIfAbsent(key, WINDOW_SECONDS)) {
            store.replace(key, Map.of(COUNT, "1"));
            return true;
        }
        Map<String, String> notes = store.get(key);
        int used = notes == null ? 0 : Integer.parseInt(notes.getOrDefault(COUNT, "1"));
        if (used >= limit) {
            return false;
        }
        store.replace(key, Map.of(COUNT, Integer.toString(used + 1)));
        return true;
    }
}
