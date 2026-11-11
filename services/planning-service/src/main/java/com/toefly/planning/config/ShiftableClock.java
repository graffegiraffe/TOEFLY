package com.toefly.planning.config;

import com.toefly.planning.application.port.in.DevClockUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** The system clock plus an offset; exists only in the profiles local and demo. */
public class ShiftableClock extends Clock implements DevClockUseCase {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public ShiftableClock(Clock base) {
        this.base = base;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    @Override
    public Instant now() {
        return instant();
    }

    @Override
    public Duration offset() {
        return offset.get();
    }

    @Override
    public Duration shift(Duration delta) {
        return offset.updateAndGet(current -> current.plus(delta));
    }

    @Override
    public void reset() {
        offset.set(Duration.ZERO);
    }
}
