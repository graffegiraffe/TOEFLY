package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.submission.Submission;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Reads of submissions for the learner and the tutors linked to them. */
public interface SubmissionQueryUseCase {

    SubmissionView get(Caller caller, UUID submissionId);

    /**
     * The submission for an internal call: a service token reads any, a user token only its own ({@code not-found}
     * otherwise); {@code versionNo} keeps only that version.
     */
    InternalSubmissionView internal(Caller caller, UUID submissionId, @Nullable Integer versionNo);

    List<Submission> list(Caller caller, @Nullable UUID studentId, SubmissionFilter filter, int offset, int limit);
}
