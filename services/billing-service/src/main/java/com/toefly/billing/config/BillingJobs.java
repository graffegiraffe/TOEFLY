package com.toefly.billing.config;

import com.toefly.billing.application.port.in.LifecycleUseCase;
import com.toefly.billing.application.port.in.PaymentSagaUseCase;
import com.toefly.billing.application.port.in.WebhookUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Pollers of the saga, stored webhooks and the subscription timers. Each row is claimed with
 * {@code FOR UPDATE SKIP LOCKED}, so two replicas never run the same step.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(value = "toefly.billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BillingJobs {

    private static final Logger log = LoggerFactory.getLogger(BillingJobs.class);

    private final PaymentSagaUseCase sagas;
    private final WebhookUseCase webhooks;
    private final LifecycleUseCase lifecycle;

    BillingJobs(PaymentSagaUseCase sagas, WebhookUseCase webhooks, LifecycleUseCase lifecycle) {
        this.sagas = sagas;
        this.webhooks = webhooks;
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${toefly.billing.jobs.saga-interval:2s}")
    void sagas() {
        run("saga", sagas::runDue);
    }

    @Scheduled(fixedDelayString = "${toefly.billing.jobs.webhook-interval:15s}")
    void webhooks() {
        run("webhooks", webhooks::processPending);
    }

    @Scheduled(fixedDelayString = "${toefly.billing.jobs.timer-interval:60s}")
    void timers() {
        run("timers", lifecycle::runTimers);
    }

    private static void run(String job, java.util.function.IntSupplier work) {
        try {
            int done = work.getAsInt();
            if (done > 0) {
                log.debug("billing job {} handled {}", job, done);
            }
        } catch (RuntimeException failure) {
            log.warn("billing job {} failed: {}", job, failure.toString());
        }
    }
}
