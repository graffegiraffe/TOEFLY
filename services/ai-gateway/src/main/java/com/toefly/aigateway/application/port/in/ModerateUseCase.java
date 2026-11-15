package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.GuardPolicy;

/** {@code POST /internal/v1/ai/moderations}: heuristics and the guard model under a policy (§3.4). */
public interface ModerateUseCase {

    GuardAssessment moderate(String text, GuardPolicy policy);
}
