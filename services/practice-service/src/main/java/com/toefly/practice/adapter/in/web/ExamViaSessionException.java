package com.toefly.practice.adapter.in.web;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** EXAM attempts are created by mockexam-service only (StartAttemptRequest.mode). */
@ProblemType(value = "business-rule-violated", status = 422)
class ExamViaSessionException extends RuntimeException implements ProblemProperties {

    ExamViaSessionException() {
        super("Exam attempts are started by an exam session.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", "exam-attempt-via-exam-session");
    }
}
