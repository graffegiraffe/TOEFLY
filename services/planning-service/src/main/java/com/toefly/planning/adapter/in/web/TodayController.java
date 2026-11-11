package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.api.TodayApi;
import com.toefly.planning.adapter.in.web.dto.Today;
import com.toefly.planning.application.port.in.PlanQueryUseCase;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Tasks for today in the learner's time zone (US-05.01, docs/03 §10.4). */
@RestController
public class TodayController implements TodayApi {

    private final PlanQueryUseCase queries;
    private final CurrentCaller caller;

    public TodayController(PlanQueryUseCase queries, CurrentCaller caller) {
        this.queries = queries;
        this.caller = caller;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Today> getToday(@Nullable LocalDate date, @Nullable String ifNoneMatch) {
        return ResponseEntity.ok(WeekDtoMapper.today(queries.today(caller.userId(), date)));
    }
}
