package com.toefly.practice.adapter.in.web;

import com.toefly.practice.adapter.in.web.IdempotentExecution.Outcome;
import com.toefly.practice.adapter.in.web.api.AdminApi;
import com.toefly.practice.adapter.in.web.dto.ProjectionRebuildJob;
import com.toefly.practice.application.port.in.RebuildProjectionUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Rebuild of attempt_view from the event store (docs/04-architecture.md §6.6). */
@RestController
public class ProjectionController implements AdminApi {

    private final RebuildProjectionUseCase rebuild;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;

    public ProjectionController(
            RebuildProjectionUseCase rebuild, CurrentCaller caller, IdempotentExecution idempotent) {
        this.rebuild = rebuild;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectionRebuildJob> rebuildAttemptView(UUID idempotencyKey) {
        return idempotent.run(
                caller.userId(), idempotencyKey, "rebuildAttemptView", null, ProjectionRebuildJob.class, () -> {
                    RebuildProjectionUseCase.Rebuild job = rebuild.rebuild();
                    return new Outcome<>(
                            HttpStatus.ACCEPTED,
                            new ProjectionRebuildJob()
                                    .jobId(job.jobId())
                                    .projection(ProjectionRebuildJob.ProjectionEnum.ATTEMPT_VIEW)
                                    .status(ProjectionRebuildJob.StatusEnum.COMPLETED)
                                    .startedAt(AttemptDtoMapper.time(job.startedAt()))
                                    .finishedAt(AttemptDtoMapper.time(job.finishedAt()))
                                    .processedEvents(job.processedEvents()),
                            "/api/v1/admin/projections/attempt-view/rebuild/" + job.jobId(),
                            null);
                });
    }
}
