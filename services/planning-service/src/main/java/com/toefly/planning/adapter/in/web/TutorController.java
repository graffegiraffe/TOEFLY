package com.toefly.planning.adapter.in.web;

import com.toefly.planning.adapter.in.web.api.TutorApi;
import com.toefly.planning.adapter.in.web.dto.PendingApprovalPage;
import com.toefly.planning.adapter.in.web.dto.StudyPlan;
import com.toefly.planning.adapter.in.web.dto.WeekSummaryList;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The workplace of the tutor opens with classroom-service in phase 10 (docs/12, phase 4, decision 5). */
@RestController
public class TutorController implements TutorApi {

    @Override
    public ResponseEntity<StudyPlan> getStudentStudyPlan(UUID studentId) {
        throw new NotImplementedException("getStudentStudyPlan");
    }

    @Override
    public ResponseEntity<WeekSummaryList> listStudentWeeks(UUID studentId) {
        throw new NotImplementedException("listStudentWeeks");
    }

    @Override
    public ResponseEntity<PendingApprovalPage> listPendingApprovals(
            String sort, Integer limit, @Nullable String cursor) {
        throw new NotImplementedException("listPendingApprovals");
    }
}
