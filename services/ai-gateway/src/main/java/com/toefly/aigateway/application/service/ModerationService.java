package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.ModerateUseCase;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import org.springframework.stereotype.Service;

/** {@code POST /internal/v1/ai/moderations}: local only, so neither masked nor journaled as an external call. */
@Service
class ModerationService implements ModerateUseCase {

    private final SafetyService safety;

    ModerationService(SafetyService safety) {
        this.safety = safety;
    }

    @Override
    public GuardAssessment moderate(String text, GuardPolicy policy) {
        GuardPolicy effective = policy == GuardPolicy.NONE ? GuardPolicy.CHAT : policy;
        return safety.check(text, effective, false);
    }
}
