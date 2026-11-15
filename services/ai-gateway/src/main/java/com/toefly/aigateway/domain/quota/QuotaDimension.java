package com.toefly.aigateway.domain.quota;

import java.time.Duration;

/** Measured resource of a provider model and the window its capacity refills over (§2.5.1). */
public enum QuotaDimension {
    RPM(Duration.ofMinutes(1), Unit.REQUESTS, false),
    RPD(Duration.ofDays(1), Unit.REQUESTS, true),
    TPM(Duration.ofMinutes(1), Unit.TOKENS, false),
    TPD(Duration.ofDays(1), Unit.TOKENS, true),
    ASH(Duration.ofHours(1), Unit.AUDIO_SECONDS, false),
    ASD(Duration.ofDays(1), Unit.AUDIO_SECONDS, true),
    MONTHLY_AUDIO_SECONDS(Duration.ofDays(30), Unit.AUDIO_SECONDS, true),
    MONTHLY_CHARACTERS(Duration.ofDays(30), Unit.CHARACTERS, true);

    /** What a dimension counts. */
    public enum Unit {
        REQUESTS,
        TOKENS,
        AUDIO_SECONDS,
        CHARACTERS
    }

    private final Duration window;
    private final Unit unit;
    private final boolean budget;

    QuotaDimension(Duration window, Unit unit, boolean budget) {
        this.window = window;
        this.unit = unit;
        this.budget = budget;
    }

    public Duration window() {
        return window;
    }

    public Unit unit() {
        return unit;
    }

    /** Daily and monthly budgets keep a reserve for higher priorities; per-minute rates do not. */
    public boolean budget() {
        return budget;
    }
}
