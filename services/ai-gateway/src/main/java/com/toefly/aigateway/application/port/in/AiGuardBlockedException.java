package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** The guard refused the text under the CHAT policy; the caller shows a refusal template (§3.6). */
@ProblemType(value = "ai-guard-blocked", status = 400)
public class AiGuardBlockedException extends RuntimeException implements ProblemProperties {

    private final GuardAssessment assessment;
    private final String refusalCode;

    public AiGuardBlockedException(GuardAssessment assessment, String refusalCode) {
        super("The guard blocked this text.");
        this.assessment = assessment;
        this.refusalCode = refusalCode;
    }

    public GuardAssessment assessment() {
        return assessment;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of(
                "verdict", assessment.verdict().name(),
                "categories", assessment.categories(),
                "refusalCode", refusalCode);
    }
}
