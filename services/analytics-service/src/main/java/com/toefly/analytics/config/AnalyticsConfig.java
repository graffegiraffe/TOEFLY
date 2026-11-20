package com.toefly.analytics.config;

import com.toefly.analytics.application.port.in.MaintainJournalUseCase;
import com.toefly.analytics.application.port.in.RebuildProjectionUseCase;
import java.time.Clock;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Clock, the executor of rebuilds, method security of the admin API and the daily maintenance of the journal. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableMethodSecurity
class AnalyticsConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    java.util.concurrent.ExecutorService rebuildExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    JournalJobs journalJobs(MaintainJournalUseCase journal, RebuildProjectionUseCase rebuilds) {
        return new JournalJobs(journal, rebuilds);
    }

    /** On start: partitions for the coming months and no rebuild left marked running; then once a day. */
    static class JournalJobs {

        private final MaintainJournalUseCase journal;
        private final RebuildProjectionUseCase rebuilds;

        JournalJobs(MaintainJournalUseCase journal, RebuildProjectionUseCase rebuilds) {
            this.journal = journal;
            this.rebuilds = rebuilds;
        }

        @EventListener(ApplicationReadyEvent.class)
        void onStart() {
            rebuilds.recoverInterrupted();
            maintain();
        }

        @Scheduled(cron = "${toefly.analytics.journal-maintenance-cron:0 15 3 * * *}", zone = "UTC")
        void maintain() {
            try {
                journal.maintain();
            } catch (RuntimeException failure) {
                log.warn("Journal maintenance failed; the default partition keeps the events", failure);
            }
        }
    }
}
