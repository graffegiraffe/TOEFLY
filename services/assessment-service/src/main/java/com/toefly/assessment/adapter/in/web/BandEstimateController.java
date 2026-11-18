package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.api.BandEstimatesApi;
import com.toefly.assessment.adapter.in.web.dto.BandEstimate;
import com.toefly.assessment.adapter.in.web.dto.BandEstimateHistory;
import com.toefly.assessment.adapter.in.web.dto.WeeklyBandEstimate;
import com.toefly.assessment.application.port.in.BandEstimateUseCase;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The band estimate for Home and its weekly history (AC-13.01.1, AC-13.02.1). */
@RestController
public class BandEstimateController implements BandEstimatesApi {

    private final BandEstimateUseCase estimates;
    private final CurrentCaller caller;

    public BandEstimateController(BandEstimateUseCase estimates, CurrentCaller caller) {
        this.estimates = estimates;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<BandEstimate> getCurrentBandEstimate(@Nullable UUID studentId) {
        return ResponseEntity.ok(AssessmentDtoMapper.estimate(estimates.current(caller.get(), studentId)));
    }

    @Override
    public ResponseEntity<BandEstimateHistory> listBandEstimates(@Nullable UUID studentId) {
        return ResponseEntity.ok(new BandEstimateHistory()
                .items(estimates.history(caller.get(), studentId).stream()
                        .map(w -> new WeeklyBandEstimate()
                                .weekStart(w.weekStart())
                                .estimate(AssessmentDtoMapper.estimate(w.estimate())))
                        .toList()));
    }
}
