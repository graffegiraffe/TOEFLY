package com.toefly.tutoragent.config;

import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.MemoryUseCase;
import com.toefly.tutoragent.application.port.in.ReengagementUseCase;
import com.toefly.tutoragent.application.port.in.WeeklyReviewUseCase;
import java.time.Clock;
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

/**
 * Background work on one replica (ShedLock): learner-model-updated after the 5-minute window, embeddings of the mistake
 * memory, deferred weekly reviews (docs/12, phase 5c, decision 8).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@ConditionalOnProperty(name = "toefly.tutor-agent.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class TutorAgentJobs {

    private static final Logger log = LoggerFactory.getLogger(TutorAgentJobs.class);

    private final LearnerModelUseCase learner;
    private final MemoryUseCase memory;
    private final WeeklyReviewUseCase reviews;
    private final ReengagementUseCase reengagement;
    private final Clock clock;

    public TutorAgentJobs(
            LearnerModelUseCase learner,
            MemoryUseCase memory,
            WeeklyReviewUseCase reviews,
            ReengagementUseCase reengagement,
            Clock clock) {
        this.learner = learner;
        this.memory = memory;
        this.reviews = reviews;
        this.reengagement = reengagement;
        this.clock = clock;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Scheduled(fixedDelayString = "${toefly.tutor-agent.publication-interval:PT30S}", initialDelayString = "PT20S")
    @SchedulerLock(name = "tutor-agent-learner-model-publication")
    public void publishLearnerModels() {
        int published = learner.publishDue(clock.instant());
        if (published > 0) {
            log.info("Published {} learner models", published);
        }
    }

    @Scheduled(fixedDelayString = "${toefly.tutor-agent.memory-interval:PT30S}", initialDelayString = "PT25S")
    @SchedulerLock(name = "tutor-agent-memory-embeddings")
    public void embedMemory() {
        memory.embedPending(32);
    }

    @Scheduled(fixedDelayString = "${toefly.tutor-agent.deferred-review-interval:PT1M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "tutor-agent-deferred-reviews")
    public void retryReviews() {
        int handled = reviews.retryDeferred(clock.instant());
        if (handled > 0) {
            log.info("Retried {} deferred weekly reviews", handled);
        }
    }

    /** Every 15 minutes: learners whose local time is 10:00–10:15 (docs/12, phase 5c, decision 7). */
    @Scheduled(cron = "${toefly.tutor-agent.reengagement-cron:0 */15 * * * *}")
    @SchedulerLock(name = "tutor-agent-reengagement")
    public void writeComeBackMessages() {
        int written = reengagement.writeDue(clock.instant());
        if (written > 0) {
            log.info("Wrote {} come-back messages", written);
        }
    }
}
