package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.ModelRef;

/** Tokens of a text before a call, with the safety margin of the model family (§2.5.1). */
public interface TokenCounterPort {

    int estimate(String text, ModelRef model);
}
