package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.safety.GuardModelVerdict;

/** Local guard model (Llama Guard 3 on Ollama); throws {@link ProviderFailure} when it is down. */
public interface GuardModelPort {

    /** Classifies a user turn, or an assistant turn when {@code output} is true. */
    GuardModelVerdict classify(String text, boolean output);
}
