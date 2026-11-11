package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.IdempotentExecution.Outcome;
import com.toefly.planning.adapter.in.web.api.StudyPlansApi;
import com.toefly.planning.adapter.in.web.dto.CreateStudyPlanRequest;
import com.toefly.planning.adapter.in.web.dto.StudyPlan;
import com.toefly.planning.adapter.in.web.dto.StudyPlanPatch;
import com.toefly.planning.adapter.in.web.dto.WeekSummaryList;
import com.toefly.planning.application.port.in.BuildStudyPlanUseCase;
import com.toefly.planning.application.port.in.ChangeStudyPlanUseCase;
import com.toefly.planning.application.port.in.PlanNotFoundException;
import com.toefly.planning.application.port.in.PlanQueryUseCase;
import com.toefly.planning.domain.plan.LearnerCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** The plan of the learner and the gallery of its weeks (docs/05-api-contracts.md §6.7). */
@RestController
public class StudyPlanController implements StudyPlansApi {

    private final BuildStudyPlanUseCase build;
    private final ChangeStudyPlanUseCase change;
    private final PlanQueryUseCase queries;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;
    private final Clock clock;

    public StudyPlanController(
            BuildStudyPlanUseCase build,
            ChangeStudyPlanUseCase change,
            PlanQueryUseCase queries,
            CurrentCaller caller,
            IdempotentExecution idempotent,
            Clock clock) {
        this.build = build;
        this.change = change;
        this.queries = queries;
        this.caller = caller;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudyPlan> createStudyPlan(UUID idempotencyKey, CreateStudyPlanRequest request) {
        UUID user = caller.userId();
        return idempotent.run(user, idempotencyKey, "createStudyPlan", request, StudyPlan.class, () -> {
            var plan = build.create(user, request.getExamDate(), request.getWeeksCount(), request.getMinutesPerDay());
            return new Outcome<>(
                    HttpStatus.CREATED,
                    WeekDtoMapper.plan(plan, today(plan)),
                    "/api/v1/study-plans/current",
                    ETags.of(plan.version()));
        });
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudyPlan> getCurrentStudyPlan(@Nullable String ifNoneMatch) {
        var plan = queries.plan(caller.userId()).orElseThrow(PlanNotFoundException::new);
        if (ETags.matches(ifNoneMatch, plan.version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(ETags.of(plan.version()))
                    .build();
        }
        return ResponseEntity.ok().eTag(ETags.of(plan.version())).body(WeekDtoMapper.plan(plan, today(plan)));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudyPlan> patchCurrentStudyPlan(String ifMatch, StudyPlanPatch patch) {
        Integer version = ETags.version(ifMatch);
        var plan = change.patch(
                caller.userId(), version == null ? -1 : version, patch.getExamDate(), patch.getMinutesPerDay());
        return ResponseEntity.ok().eTag(ETags.of(plan.version())).body(WeekDtoMapper.plan(plan, today(plan)));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<WeekSummaryList> listCurrentPlanWeeks() {
        UUID user = caller.userId();
        LocalDate today = queries.plan(user).map(this::today).orElse(LocalDate.now(clock));
        return ResponseEntity.ok(new WeekSummaryList()
                .items(queries.weeks(user).stream()
                        .map(slot -> WeekDtoMapper.summary(slot, today))
                        .toList()));
    }

    private LocalDate today(com.toefly.planning.domain.plan.StudyPlan plan) {
        return LearnerCalendar.today(clock.instant(), plan.timezone());
    }
}
