package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.IdempotencyKeyReuseException;
import com.toefly.analytics.application.port.in.ProjectionName;
import com.toefly.analytics.application.port.in.RebuildInProgressException;
import com.toefly.analytics.application.port.in.RebuildJob;
import com.toefly.analytics.application.port.in.RebuildNotFoundException;
import com.toefly.analytics.application.port.in.RebuildProjectionUseCase;
import com.toefly.analytics.application.port.in.RebuildStart;
import com.toefly.analytics.application.port.out.RebuildJobRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Starts rebuilds in the background ({@code 202}) and reports their state. */
@Service
public class RebuildService implements RebuildProjectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

    private final RebuildJobRepository jobs;
    private final ProjectionReplayer replayer;
    private final Executor executor;
    private final Clock clock;

    public RebuildService(
            RebuildJobRepository jobs,
            ProjectionReplayer replayer,
            @Qualifier("rebuildExecutor") Executor executor,
            Clock clock) {
        this.jobs = jobs;
        this.replayer = replayer;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public RebuildStart start(ProjectionName projection, UUID adminId, UUID idempotencyKey) {
        RebuildJob earlier = jobs.findByKey(adminId, idempotencyKey).orElse(null);
        if (earlier != null) {
            if (earlier.projection() != projection) {
                throw new IdempotencyKeyReuseException();
            }
            return new RebuildStart(earlier, true);
        }
        RebuildJob job = new RebuildJob(
                UUID.randomUUID(), projection, RebuildJob.Status.RUNNING, adminId, clock.instant(), null, 0, null);
        if (!jobs.start(job, idempotencyKey)) {
            throw new RebuildInProgressException(projection);
        }
        executor.execute(() -> run(job));
        return new RebuildStart(job, false);
    }

    @Override
    public RebuildJob get(UUID jobId) {
        return jobs.find(jobId).orElseThrow(RebuildNotFoundException::new);
    }

    @Override
    public void recoverInterrupted() {
        jobs.failRunning("Interrupted by a restart");
    }

    void run(RebuildJob job) {
        try {
            long events = replayer.replay(job.projection());
            jobs.update(job.completed(events, clock.instant()));
            log.info("Rebuilt {} from {} journal events", job.projection().path(), events);
        } catch (RuntimeException failure) {
            log.error("Rebuild of {} failed", job.projection().path(), failure);
            jobs.update(job.failed(failure.getClass().getSimpleName(), clock.instant()));
        }
    }
}
