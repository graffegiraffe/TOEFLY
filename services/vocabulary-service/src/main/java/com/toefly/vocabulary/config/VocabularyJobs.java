package com.toefly.vocabulary.config;

import com.toefly.vocabulary.application.port.in.DependencyUnavailableException;
import com.toefly.vocabulary.application.port.in.EnrichmentUseCase;
import com.toefly.vocabulary.application.port.in.WordSetUseCase;
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
 * Background jobs on one replica: copying the Collins units from content-service and enriching words saved while
 * ai-gateway was unavailable (docs/04 §3.12).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@ConditionalOnProperty(name = "toefly.vocabulary.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class VocabularyJobs {

    private static final Logger log = LoggerFactory.getLogger(VocabularyJobs.class);

    private final WordSetUseCase wordSets;
    private final EnrichmentUseCase enrichment;
    private final VocabularyProperties properties;

    public VocabularyJobs(WordSetUseCase wordSets, EnrichmentUseCase enrichment, VocabularyProperties properties) {
        this.wordSets = wordSets;
        this.enrichment = enrichment;
        this.properties = properties;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Scheduled(fixedDelayString = "${toefly.vocabulary.jobs.word-set-sync:PT10M}", initialDelayString = "PT10S")
    @SchedulerLock(name = "vocabulary-word-set-sync")
    public void syncWordSets() {
        try {
            int sets = wordSets.sync();
            log.debug("Copied {} Collins Vocabulary units", sets);
        } catch (DependencyUnavailableException unavailable) {
            log.warn("Collins units not copied: {}", unavailable.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${toefly.vocabulary.jobs.enrichment:PT2M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "vocabulary-enrichment")
    public void enrich() {
        int enriched = enrichment.enrichPending(properties.jobs().enrichmentBatch());
        if (enriched > 0) {
            log.info("Enriched {} words", enriched);
        }
    }
}
