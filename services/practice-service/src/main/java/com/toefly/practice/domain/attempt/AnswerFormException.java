package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** The form of the answer does not fit the task type, for example a text for a multiple-choice question. */
@ProblemType(value = "business-rule-violated", status = 422)
public class AnswerFormException extends RuntimeException implements ProblemProperties {

    public AnswerFormException(String message) {
        super(message);
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", "answer-form");
    }
}
