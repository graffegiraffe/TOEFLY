package com.toefly.aigateway.config;

import com.toefly.aigateway.adapter.out.persistence.JdbcProviderQuotaAdapter;
import com.toefly.aigateway.adapter.out.template.RoutesFileAdapter;
import com.toefly.aigateway.application.port.in.PromptRegistryUseCase;
import com.toefly.aigateway.application.port.in.UsageUseCase;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Start-up sync of prompts and quotas, the reload of routes and the retention of the journal. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class AiGatewayJobs {

    private static final Logger LOG = LoggerFactory.getLogger(AiGatewayJobs.class);

    private final PromptRegistryUseCase prompts;
    private final UsageUseCase usage;
    private final RoutesFileAdapter routes;
    private final JdbcProviderQuotaAdapter quotas;
    private final AiGatewayProperties properties;

    AiGatewayJobs(
            PromptRegistryUseCase prompts,
            UsageUseCase usage,
            RoutesFileAdapter routes,
            JdbcProviderQuotaAdapter quotas,
            AiGatewayProperties properties) {
        this.prompts = prompts;
        this.usage = usage;
        this.routes = routes;
        this.quotas = quotas;
        this.properties = properties;
    }

    @Bean
    LockProvider lockProvider(JdbcTemplate jdbc) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .usingDbTime()
                .build());
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStart() {
        quotas.seed(AiGatewayConfig.defaultQuotas(properties));
        int added = prompts.syncSources();
        LOG.info("Prompt registry synced: {} new versions", added);
    }

    @Scheduled(fixedDelayString = "PT30S")
    void reloadRoutes() {
        routes.refresh();
    }

    @Scheduled(cron = "0 17 3 * * *")
    @SchedulerLock(name = "ai-call-retention")
    void retention() {
        usage.applyRetention();
    }
}
