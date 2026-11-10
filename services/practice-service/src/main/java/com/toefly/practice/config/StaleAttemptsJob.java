package com.toefly.practice.config;

import com.toefly.practice.application.port.in.AbandonAttemptUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly: abandons idle attempts, 7 days in Practice and 24 hours in Exam (AC-06.03.3). */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "toefly.practice.stale-attempts.enabled", havingValue = "true", matchIfMissing = true)
public class StaleAttemptsJob {

    private static final Logger log = LoggerFactory.getLogger(StaleAttemptsJob.class);

    private final AbandonAttemptUseCase abandon;

    public StaleAttemptsJob(AbandonAttemptUseCase abandon) {
        this.abandon = abandon;
    }

    @Scheduled(cron = "${toefly.practice.stale-attempts.cron:0 17 * * * *}")
    public void run() {
        int abandoned = abandon.abandonStale();
        if (abandoned > 0) {
            log.info("Abandoned {} idle attempts", abandoned);
        }
    }
}
