package com.toefly.assessment.domain.submission;

/**
 * A rule of the submission is broken. The code is the problem slug of the API: {@code not-found},
 * {@code submission-not-ready}, {@code conflict}, {@code validation-failed}.
 */
public class SubmissionRuleException extends RuntimeException {

    private final String code;

    public SubmissionRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
