package com.toefly.assessment.config;

import com.toefly.assessment.application.port.in.DeferredEvaluationUseCase;
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

/** Retries of EVALUATION_DEFERRED once a minute on one replica (docs/12 phase 5b decision 6). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@ConditionalOnProperty(name = "toefly.assessment.deferred.enabled", havingValue = "true", matchIfMissing = true)
public class DeferredEvaluationJob {

    private static final Logger log = LoggerFactory.getLogger(DeferredEvaluationJob.class);

    private final DeferredEvaluationUseCase deferred;

    public DeferredEvaluationJob(DeferredEvaluationUseCase deferred) {
        this.deferred = deferred;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Scheduled(fixedDelayString = "${toefly.assessment.deferred.interval:PT1M}", initialDelayString = "PT15S")
    @SchedulerLock(name = "assessment-deferred-evaluations")
    public void run() {
        int sent = deferred.retryDue();
        if (sent > 0) {
            log.info("Sent {} deferred evaluations again", sent);
        }
    }
}
