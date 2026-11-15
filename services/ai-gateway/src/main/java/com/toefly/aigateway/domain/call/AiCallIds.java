package com.toefly.aigateway.domain.call;

import java.security.SecureRandom;
import java.time.Instant;

/** Journal ids {@code aic_<ULID>}: 48 bits of milliseconds and 80 random bits in Crockford base32, sortable by time. */
public final class AiCallIds {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AiCallIds() {}

    public static String next(Instant now) {
        char[] ulid = new char[26];
        long time = now.toEpochMilli();
        for (int i = 9; i >= 0; i--) {
            ulid[i] = CROCKFORD[(int) (time & 31)];
            time >>>= 5;
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long hi = 0;
        long lo = 0;
        for (int i = 0; i < 5; i++) {
            hi = (hi << 8) | (random[i] & 0xff);
            lo = (lo << 8) | (random[i + 5] & 0xff);
        }
        for (int i = 17; i >= 10; i--) {
            ulid[i] = CROCKFORD[(int) (hi & 31)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            ulid[i] = CROCKFORD[(int) (lo & 31)];
            lo >>>= 5;
        }
        return "aic_" + new String(ulid);
    }
}
