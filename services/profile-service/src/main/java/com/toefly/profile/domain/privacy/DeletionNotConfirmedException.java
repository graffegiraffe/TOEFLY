package com.toefly.profile.domain.privacy;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

@ProblemType(value = "business-rule-violated", status = 422)
public class DeletionNotConfirmedException extends RuntimeException implements ProblemProperties {

    public DeletionNotConfirmedException() {
        super("Type DELETE to confirm.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", "deletion-confirmation");
    }
}
