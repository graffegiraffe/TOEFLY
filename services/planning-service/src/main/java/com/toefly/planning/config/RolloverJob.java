package com.toefly.planning.config;

import com.toefly.planning.application.port.in.RolloverUseCase;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** The local midnight of every learner, checked every 5 minutes on one replica (docs/12, phase 4, decision 7). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@ConditionalOnProperty(name = "toefly.planning.rollover.enabled", havingValue = "true", matchIfMissing = true)
public class RolloverJob {

    private static final Logger log = LoggerFactory.getLogger(RolloverJob.class);

    private final RolloverUseCase rollover;

    public RolloverJob(RolloverUseCase rollover) {
        this.rollover = rollover;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Scheduled(fixedDelayString = "${toefly.planning.rollover.interval:PT5M}", initialDelayString = "PT20S")
    @SchedulerLock(name = "planning-day-rollover")
    public void run() {
        int changed = rollover.rolloverAll();
        if (changed > 0) {
            log.info("Rolled over the day of {} plans", changed);
        }
    }
}
