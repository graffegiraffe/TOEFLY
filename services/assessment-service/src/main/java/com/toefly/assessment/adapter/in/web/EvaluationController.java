package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.api.EvaluationsApi;
import com.toefly.assessment.adapter.in.web.dto.Evaluation;
import com.toefly.assessment.adapter.in.web.dto.EvaluationList;
import com.toefly.assessment.adapter.in.web.dto.TutorEvaluationRequest;
import com.toefly.assessment.application.port.in.SubmissionCommandUseCase;
import com.toefly.assessment.application.port.in.SubmissionQueryUseCase;
import com.toefly.assessment.application.port.in.TutorEvaluationCommand;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Evaluations of AI and the tutor's score (docs/05-api-contracts.md §6.5). */
@RestController
public class EvaluationController implements EvaluationsApi {

    private final SubmissionCommandUseCase commands;
    private final SubmissionQueryUseCase queries;
    private final CurrentCaller caller;

    public EvaluationController(
            SubmissionCommandUseCase commands, SubmissionQueryUseCase queries, CurrentCaller caller) {
        this.commands = commands;
        this.queries = queries;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<EvaluationList> listEvaluations(UUID submissionId) {
        return ResponseEntity.ok(new EvaluationList()
                .items(queries.get(caller.get(), submissionId).evaluations().stream()
                        .map(AssessmentDtoMapper::evaluation)
                        .toList()));
    }

    @Override
    public ResponseEntity<Evaluation> putTutorEvaluation(
            String ifMatch, UUID submissionId, TutorEvaluationRequest request) {
        var evaluation = commands.tutorEvaluation(
                caller.get(),
                submissionId,
                new TutorEvaluationCommand(
                        request.getVersionNo(),
                        request.getScore().doubleValue(),
                        request.getCriteria(),
                        request.getNote() == null ? "" : request.getNote()),
                ETags.version(ifMatch));
        return ResponseEntity.ok(AssessmentDtoMapper.evaluation(evaluation));
    }
}
