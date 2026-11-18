package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.SubmissionQueryUseCase;
import com.toefly.assessment.application.port.in.SubmissionView;
import com.toefly.assessment.application.port.out.DeferredEvaluationRepository;
import com.toefly.assessment.application.port.out.DependencyUnavailableException;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionRuleException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Reads of submissions; the task comes from content-service and is left out if it is unavailable. */
@Service
class SubmissionQueryService implements SubmissionQueryUseCase {

    private final SubmissionRepository submissions;
    private final DeferredEvaluationRepository deferred;
    private final TaskContentPort content;
    private final Access access;

    SubmissionQueryService(
            SubmissionRepository submissions,
            DeferredEvaluationRepository deferred,
            TaskContentPort content,
            Access access) {
        this.submissions = submissions;
        this.deferred = deferred;
        this.content = content;
        this.access = access;
    }

    @Override
    public SubmissionView get(Caller caller, UUID submissionId) {
        Submission submission = submissions
                .find(submissionId)
                .orElseThrow(() -> new SubmissionRuleException("not-found", "No such submission."));
        access.read(caller, submission.userId());
        TaskContent task;
        try {
            task = content.load(submission.itemSetId()).orElse(null);
        } catch (DependencyUnavailableException unavailable) {
            task = null;
        }
        return new SubmissionView(
                submission,
                submissions.evaluations(submissionId),
                task,
                deferred.find(submissionId, submission.current().versionNo()).orElse(null));
    }

    @Override
    public List<Submission> list(
            Caller caller, @Nullable UUID studentId, SubmissionRepository.Filter filter, int offset, int limit) {
        return submissions.list(access.student(caller, studentId), filter, offset, limit);
    }
}
