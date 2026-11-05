package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** A value breaks a profile invariant; {@code rule} names the broken rule for the client. */
@ProblemType(value = "business-rule-violated", status = 422)
public class InvalidProfileValueException extends RuntimeException implements ProblemProperties {

    private final String rule;

    public InvalidProfileValueException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", rule);
    }
}
