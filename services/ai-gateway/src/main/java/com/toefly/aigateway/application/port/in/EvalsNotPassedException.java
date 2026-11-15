package com.toefly.aigateway.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** A version becomes active only after a passed eval run of that very version (INV-AIG-01). */
@ProblemType(value = "evals-not-passed", status = 409)
public class EvalsNotPassedException extends RuntimeException implements ProblemProperties {

    private final String promptKey;
    private final int version;

    public EvalsNotPassedException(String promptKey, int version) {
        super(promptKey + "@" + version + " has no passed eval run.");
        this.promptKey = promptKey;
        this.version = version;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("promptKey", promptKey, "version", version);
    }
}
