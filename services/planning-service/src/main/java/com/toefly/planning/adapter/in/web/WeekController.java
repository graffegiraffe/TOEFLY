package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.api.WeeksApi;
import com.toefly.planning.adapter.in.web.dto.AddWeekTaskRequest;
import com.toefly.planning.adapter.in.web.dto.ApproveWeekRequest;
import com.toefly.planning.adapter.in.web.dto.RebalanceWeekRequest;
import com.toefly.planning.adapter.in.web.dto.ReorderWeekTasksRequest;
import com.toefly.planning.adapter.in.web.dto.Week;
import com.toefly.planning.adapter.in.web.dto.WeekTask;
import com.toefly.planning.adapter.in.web.dto.WeekTaskPatch;
import com.toefly.planning.application.port.in.PlanQueryUseCase;
import com.toefly.planning.application.port.in.WeekTaskUseCase;
import com.toefly.planning.domain.week.WeekRuleException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** A week of the learner; editing by the tutor opens in phase 10 (docs/12, phase 4, decision 5). */
@RestController
public class WeekController implements WeeksApi {

    private final PlanQueryUseCase queries;
    private final WeekTaskUseCase tasks;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;

    public WeekController(
            PlanQueryUseCase queries, WeekTaskUseCase tasks, CurrentCaller caller, IdempotentExecution idempotent) {
        this.queries = queries;
        this.tasks = tasks;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Week> getWeek(UUID weekId, @Nullable String ifNoneMatch) {
        var view = queries.week(caller.userId(), weekId)
                .orElseThrow(() -> new WeekRuleException("not-found", "No week " + weekId));
        int version = view.week().version();
        if (ETags.matches(ifNoneMatch, version)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(ETags.of(version))
                    .build();
        }
        return ResponseEntity.ok().eTag(ETags.of(version)).body(WeekDtoMapper.week(view));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WeekTask> patchWeekTask(String ifMatch, UUID weekId, UUID taskId, WeekTaskPatch patch) {
        if (patch.getDayIndex() == null || patch.getNote() != null || patch.getResourceUrl() != null) {
            throw new WeekRuleException("validation-failed", "A learner changes only dayIndex");
        }
        Integer version = ETags.version(ifMatch);
        UUID user = caller.userId();
        tasks.move(user, weekId, taskId, version == null ? -1 : version, patch.getDayIndex());
        return taskResponse(user, weekId, taskId);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Week> rebalanceWeek(
            UUID idempotencyKey, UUID weekId, @Nullable RebalanceWeekRequest request) {
        UUID user = caller.userId();
        return idempotent.run(user, idempotencyKey, "rebalanceWeek:" + weekId, request, Week.class, () -> {
            tasks.rebalance(user, weekId);
            return weekOutcome(user, weekId);
        });
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Week> finishWeekEarly(UUID idempotencyKey, UUID weekId) {
        UUID user = caller.userId();
        return idempotent.run(user, idempotencyKey, "finishWeekEarly:" + weekId, null, Week.class, () -> {
            tasks.finishEarly(user, weekId);
            return weekOutcome(user, weekId);
        });
    }

    private IdempotentExecution.Outcome<Week> weekOutcome(UUID user, UUID weekId) {
        var view = queries.week(user, weekId).orElseThrow();
        return IdempotentExecution.Outcome.ok(
                WeekDtoMapper.week(view), ETags.of(view.week().version()));
    }

    private ResponseEntity<WeekTask> taskResponse(UUID user, UUID weekId, UUID taskId) {
        var view = queries.week(user, weekId).orElseThrow();
        return ResponseEntity.ok()
                .eTag(ETags.of(view.week().version()))
                .body(WeekDtoMapper.task(view.week(), view.week().task(taskId)));
    }

    @Override
    public ResponseEntity<String> streamWeekEvents(UUID weekId, @Nullable String lastEventID) {
        throw new NotImplementedException("streamWeekEvents");
    }

    @Override
    public ResponseEntity<WeekTask> addWeekTask(
            UUID idempotencyKey, String ifMatch, UUID weekId, AddWeekTaskRequest request) {
        throw new NotImplementedException("addWeekTask");
    }

    @Override
    public ResponseEntity<Void> deleteWeekTask(String ifMatch, UUID weekId, UUID taskId) {
        throw new NotImplementedException("deleteWeekTask");
    }

    @Override
    public ResponseEntity<Week> reorderWeekTasks(String ifMatch, UUID weekId, ReorderWeekTasksRequest request) {
        throw new NotImplementedException("reorderWeekTasks");
    }

    @Override
    public ResponseEntity<Week> approveWeek(UUID idempotencyKey, UUID weekId, @Nullable ApproveWeekRequest request) {
        throw new NotImplementedException("approveWeek");
    }
}
