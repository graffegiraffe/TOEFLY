package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.api.AdminAssessmentApi;
import com.toefly.assessment.adapter.in.web.dto.DeferredEvaluationPage;
import com.toefly.assessment.adapter.in.web.dto.PageInfo;
import com.toefly.assessment.application.port.in.DeferredEvaluationUseCase;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** The admin queue of deferred evaluations and its manual retry (AC-11.06.4). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAssessmentController implements AdminAssessmentApi {

    private final DeferredEvaluationUseCase deferred;

    public AdminAssessmentController(DeferredEvaluationUseCase deferred) {
        this.deferred = deferred;
    }

    @Override
    public ResponseEntity<DeferredEvaluationPage> listDeferredEvaluations(@Nullable String cursor, Integer limit) {
        int offset = Cursors.offset(cursor);
        var rows = deferred.page(offset, limit + 1);
        boolean more = rows.size() > limit;
        return ResponseEntity.ok(new DeferredEvaluationPage()
                .items(rows.stream()
                        .limit(limit)
                        .map(AssessmentDtoMapper::deferredRow)
                        .toList())
                .page(new PageInfo()
                        .hasMore(more)
                        .limit(limit)
                        .nextCursor(more ? Cursors.next(offset + limit) : null)));
    }

    @Override
    public ResponseEntity<Void> retryDeferredEvaluation(UUID submissionId) {
        deferred.retryNow(submissionId);
        return ResponseEntity.accepted().build();
    }
}
