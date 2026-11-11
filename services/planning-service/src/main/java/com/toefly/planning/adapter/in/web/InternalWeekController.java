package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.api.InternalWeeksApi;
import com.toefly.planning.adapter.in.web.dto.InternalAddWeekTaskRequest;
import com.toefly.planning.adapter.in.web.dto.PlanningUserDataExport;
import com.toefly.planning.adapter.in.web.dto.Week;
import com.toefly.planning.adapter.in.web.dto.WeekTask;
import com.toefly.planning.application.port.in.PlanQueryUseCase;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.week.WeekRuleException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code /internal/v1}: weeks for tutor-agent and the export for profile-service; a service token is required. */
@RestController
public class InternalWeekController implements InternalWeeksApi {

    private final PlanQueryUseCase queries;
    private final Clock clock;

    public InternalWeekController(PlanQueryUseCase queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<Week> getCurrentWeekOfUserInternal(UUID userId) {
        var view = queries.currentWeek(userId)
                .orElseThrow(() -> new WeekRuleException("not-found", "No current week of " + userId));
        return ResponseEntity.ok().eTag(ETags.of(view.week().version())).body(WeekDtoMapper.week(view));
    }

    @Override
    public ResponseEntity<Week> getWeekInternal(UUID weekId) {
        var view = queries.weekById(weekId).orElseThrow(() -> new WeekRuleException("not-found", "No week " + weekId));
        return ResponseEntity.ok().eTag(ETags.of(view.week().version())).body(WeekDtoMapper.week(view));
    }

    @Override
    public ResponseEntity<PlanningUserDataExport> exportPlanningUserData(UUID userId) {
        var export = new PlanningUserDataExport()
                .userId(userId)
                .generatedAt(clock.instant().atOffset(ZoneOffset.UTC))
                .weeks(List.of());
        queries.export(userId)
                .ifPresent(data -> export.studyPlan(WeekDtoMapper.plan(
                                data.plan(),
                                LearnerCalendar.today(
                                        clock.instant(), data.plan().timezone())))
                        .weeks(data.weeks().stream().map(WeekDtoMapper::week).toList()));
        return ResponseEntity.ok(export);
    }

    @Override
    public ResponseEntity<WeekTask> addWeekTaskInternal(
            UUID idempotencyKey, UUID weekId, InternalAddWeekTaskRequest request) {
        throw new NotImplementedException("addWeekTaskInternal");
    }
}
