package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.adapter.in.web.api.ActivityApi;
import com.toefly.analytics.adapter.in.web.dto.ActivityDayList;
import com.toefly.analytics.adapter.in.web.dto.TimeReport;
import com.toefly.analytics.application.port.in.ActivityQueryUseCase;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Progress → Time: activity days and time against ETS norms (US-16.02). */
@RestController
public class ActivityController implements ActivityApi {

    private final ActivityQueryUseCase activity;
    private final CurrentCaller caller;

    public ActivityController(ActivityQueryUseCase activity, CurrentCaller caller) {
        this.activity = activity;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<ActivityDayList> listActivityDays(LocalDate from, LocalDate to, @Nullable UUID studentId) {
        return ResponseEntity.ok(new ActivityDayList()
                .items(activity.days(caller.get(), studentId, from, to).stream()
                        .map(AnalyticsDtoMapper::activityDay)
                        .toList()));
    }

    @Override
    public ResponseEntity<TimeReport> getTimeReport(
            @Nullable LocalDate from, @Nullable LocalDate to, @Nullable UUID studentId) {
        return ResponseEntity.ok(AnalyticsDtoMapper.timeReport(activity.timeReport(caller.get(), studentId, from, to)));
    }
}
