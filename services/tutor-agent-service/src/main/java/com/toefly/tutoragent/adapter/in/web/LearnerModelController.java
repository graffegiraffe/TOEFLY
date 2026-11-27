package com.toefly.tutoragent.adapter.in.web;

import com.toefly.tutoragent.adapter.in.web.api.LearnerModelApi;
import com.toefly.tutoragent.adapter.in.web.dto.LearnerModel;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** The learner model of the caller; a tutor's view of a student comes with phase 10. */
@RestController
@PreAuthorize("hasRole('STUDENT')")
class LearnerModelController implements LearnerModelApi {

    private final LearnerModelUseCase learner;
    private final CurrentUser user;
    private final Taxonomy taxonomy;

    LearnerModelController(LearnerModelUseCase learner, CurrentUser user, Taxonomy taxonomy) {
        this.learner = learner;
        this.user = user;
        this.taxonomy = taxonomy;
    }

    @Override
    public ResponseEntity<LearnerModel> getLearnerModel() {
        return ResponseEntity.ok(TutorAgentDtoMapper.learnerModel(learner.get(user.id()), taxonomy));
    }
}
