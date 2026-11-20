package com.toefly.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.analytics.application.port.in.IdempotencyKeyReuseException;
import com.toefly.analytics.application.port.in.ProjectionName;
import com.toefly.analytics.application.port.in.RebuildInProgressException;
import com.toefly.analytics.application.port.in.RebuildJob;
import com.toefly.analytics.application.port.in.RebuildNotFoundException;
import com.toefly.analytics.application.port.in.RebuildStart;
import com.toefly.analytics.application.port.in.event.ProfileCreated;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RebuildServiceTest {

    final Fakes fakes = new Fakes();
    final List<Runnable> queued = new ArrayList<>();
    final RebuildService service = new RebuildService(fakes.jobs, fakes.replayer, queued::add, fakes.clock);
    final UUID admin = UUID.randomUUID();

    @Test
    @DisplayName("docs/05 §6.14: a rebuild starts in the background and completes with the number of replayed events")
    void completes() {
        fakes.record(new ProfileCreated(UUID.randomUUID(), List.of("STUDENT"), Instant.parse("2026-11-19T10:00:00Z")));

        RebuildStart start = service.start(ProjectionName.PRODUCT_METRICS, admin, UUID.randomUUID());
        assertThat(start.job().status()).isEqualTo(RebuildJob.Status.RUNNING);
        queued.forEach(Runnable::run);

        RebuildJob done = service.get(start.job().id());
        assertThat(done.status()).isEqualTo(RebuildJob.Status.COMPLETED);
        assertThat(done.eventsReplayed()).isEqualTo(1);
        assertThat(done.finishedAt()).isNotNull();
    }

    @Test
    @DisplayName("docs/05 §2.6: the same Idempotency-Key returns the same job; another read model with it is rejected")
    void idempotent() {
        UUID key = UUID.randomUUID();
        RebuildStart first = service.start(ProjectionName.ACTIVITY, admin, key);

        RebuildStart again = service.start(ProjectionName.ACTIVITY, admin, key);

        assertThat(again.replayed()).isTrue();
        assertThat(again.job().id()).isEqualTo(first.job().id());
        assertThat(queued).hasSize(1);
        assertThatThrownBy(() -> service.start(ProjectionName.AI_USAGE, admin, key))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    @DisplayName("docs/05 §6.14: a second rebuild of a read model that is being rebuilt is a conflict")
    void conflict() {
        service.start(ProjectionName.STUDENT_DASHBOARD, admin, UUID.randomUUID());

        assertThatThrownBy(() -> service.start(ProjectionName.STUDENT_DASHBOARD, admin, UUID.randomUUID()))
                .isInstanceOf(RebuildInProgressException.class);
        assertThat(service.start(ProjectionName.AI_USAGE, admin, UUID.randomUUID())
                        .replayed())
                .isFalse();
    }

    @Test
    @DisplayName("ADR-0011: a failed replay marks the job failed; a restart releases jobs left running")
    void failureAndRecovery() {
        EventJournalRepository broken = new Fakes.Journal() {
            @Override
            public long replay(
                    java.util.function.Consumer<com.toefly.analytics.application.port.in.event.AnalyticsEvent> sink) {
                throw new IllegalStateException("broken journal");
            }
        };
        RebuildService failing = new RebuildService(
                fakes.jobs, new ProjectionReplayer(broken, fakes.projections), Runnable::run, fakes.clock);

        RebuildJob job =
                failing.start(ProjectionName.AI_USAGE, admin, UUID.randomUUID()).job();
        assertThat(failing.get(job.id()).status()).isEqualTo(RebuildJob.Status.FAILED);
        assertThat(failing.get(job.id()).error()).isEqualTo("IllegalStateException");

        RebuildJob stuck =
                service.start(ProjectionName.ACTIVITY, admin, UUID.randomUUID()).job();
        service.recoverInterrupted();
        assertThat(service.get(stuck.id()).status()).isEqualTo(RebuildJob.Status.FAILED);
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(RebuildNotFoundException.class);
        assertThat(ProjectionName.fromPath("activity")).contains(ProjectionName.ACTIVITY);
        assertThat(ProjectionName.fromPath("nope")).isEmpty();
    }

    @Test
    @DisplayName("docs/03 §6.15: the journal keeps partitions ahead and drops events after 400 days")
    void maintenance() {
        Instant old = fakes.clock.instant().minus(java.time.Duration.ofDays(401));
        fakes.record(new ProfileCreated(UUID.randomUUID(), List.of("STUDENT"), old));
        fakes.record(new ProfileCreated(UUID.randomUUID(), List.of("STUDENT"), fakes.clock.instant()));

        new JournalMaintenanceService(fakes.journal, fakes.clock).maintain();

        assertThat(fakes.journal.entries).hasSize(1);
    }
}
