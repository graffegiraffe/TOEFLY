package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.api.WeekTasksApi;
import com.toefly.planning.adapter.in.web.dto.CompleteWeekTaskRequest;
import com.toefly.planning.adapter.in.web.dto.SkipWeekTaskRequest;
import com.toefly.planning.adapter.in.web.dto.WeekTask;
import com.toefly.planning.application.port.in.PlanQueryUseCase;
import com.toefly.planning.application.port.in.WeekTaskUseCase;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** "Mark as done", its undo and the skip of a task (AC-04.03.3). */
@RestController
public class WeekTaskController implements WeekTasksApi {

    private final WeekTaskUseCase tasks;
    private final PlanQueryUseCase queries;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;

    public WeekTaskController(
            WeekTaskUseCase tasks, PlanQueryUseCase queries, CurrentCaller caller, IdempotentExecution idempotent) {
        this.tasks = tasks;
        this.queries = queries;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WeekTask> completeWeekTask(
            UUID idempotencyKey, UUID taskId, @Nullable CompleteWeekTaskRequest request) {
        UUID user = caller.userId();
        return idempotent.run(
                user,
                idempotencyKey,
                "completeWeekTask:" + taskId,
                request,
                WeekTask.class,
                () -> outcome(user, taskId, id -> tasks.complete(user, id)));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WeekTask> uncompleteWeekTask(UUID taskId) {
        UUID user = caller.userId();
        var outcome = outcome(user, taskId, id -> tasks.uncomplete(user, id));
        return ResponseEntity.ok().eTag(outcome.etag()).body(outcome.body());
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WeekTask> skipWeekTask(
            UUID idempotencyKey, UUID taskId, @Nullable SkipWeekTaskRequest request) {
        UUID user = caller.userId();
        return idempotent.run(
                user,
                idempotencyKey,
                "skipWeekTask:" + taskId,
                request,
                WeekTask.class,
                () -> outcome(user, taskId, id -> tasks.skip(user, id)));
    }

    private IdempotentExecution.Outcome<WeekTask> outcome(
            UUID user, UUID taskId, Function<UUID, com.toefly.planning.domain.week.WeekTask> command) {
        command.apply(taskId);
        var view = queries.weekOfTask(user, taskId);
        return IdempotentExecution.Outcome.ok(
                WeekDtoMapper.task(view.week(), view.week().task(taskId)),
                ETags.of(view.week().version()));
    }
}
