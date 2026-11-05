package com.toefly.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HourlyLimitTest {

    static final class MemoryStore implements HourlyLimit.Store {
        final Map<String, Map<String, String>> rows = new HashMap<>();
        long lifespan;

        @Override
        public Map<String, String> get(String key) {
            return rows.get(key);
        }

        @Override
        public boolean putIfAbsent(String key, long lifespanSeconds) {
            lifespan = lifespanSeconds;
            return rows.putIfAbsent(key, Map.of()) == null;
        }

        @Override
        public boolean replace(String key, Map<String, String> notes) {
            return rows.replace(key, notes) != null;
        }
    }

    @Test
    @DisplayName("AC-01.02.4 the sixth resend within an hour is rejected")
    void sixthResendRejected() {
        MemoryStore store = new MemoryStore();
        HourlyLimit limit = new HourlyLimit(store, HourlyLimitedVerifyEmail.RESENDS_PER_HOUR);

        for (int i = 0; i < 5; i++) {
            assertTrue(limit.tryAcquire("user-1"), "resend " + (i + 1));
        }
        assertFalse(limit.tryAcquire("user-1"));
        assertEquals(HourlyLimit.WINDOW_SECONDS, store.lifespan);
    }

    @Test
    @DisplayName("AC-01.02.4 users have separate limits")
    void separateUsers() {
        HourlyLimit limit = new HourlyLimit(new MemoryStore(), 1);

        assertTrue(limit.tryAcquire("user-1"));
        assertFalse(limit.tryAcquire("user-1"));
        assertTrue(limit.tryAcquire("user-2"));
    }
}
