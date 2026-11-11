package com.toefly.planning.adapter.in.web;

import com.toefly.planning.application.port.in.DevClockUseCase;
import com.toefly.planning.application.port.in.RolloverUseCase;
import java.time.Duration;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo of carry-over and rebalance: moves the clock of planning-service and runs the nightly check at once.
 * Only in the profiles local and demo, on port 8087, never behind the gateway (docs/12, phase 4, decision 7).
 */
@RestController
@Profile({"local", "demo"})
public class DevClockController {

    private final DevClockUseCase clock;
    private final RolloverUseCase rollover;

    public DevClockController(DevClockUseCase clock, RolloverUseCase rollover) {
        this.clock = clock;
        this.rollover = rollover;
    }

    @GetMapping("/dev/v1/clock")
    Map<String, Object> current() {
        return Map.of("now", clock.now().toString(), "offset", clock.offset().toString());
    }

    /** {@code POST /dev/v1/clock?shift=P1D} moves the clock by an ISO-8601 duration and runs the nightly check. */
    @PostMapping("/dev/v1/clock")
    Map<String, Object> shift(@RequestParam("shift") String shift) {
        clock.shift(Duration.parse(shift));
        int changed = rollover.rolloverAll();
        return Map.of("now", clock.now().toString(), "offset", clock.offset().toString(), "plansChanged", changed);
    }

    @DeleteMapping("/dev/v1/clock")
    Map<String, Object> reset() {
        clock.reset();
        return current();
    }

    @PostMapping("/dev/v1/rollover")
    Map<String, Object> rollover() {
        return Map.of("plansChanged", rollover.rolloverAll());
    }
}
