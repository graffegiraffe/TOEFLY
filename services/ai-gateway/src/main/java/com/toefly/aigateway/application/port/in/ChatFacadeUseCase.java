package com.toefly.aigateway.application.port.in;

import java.util.function.Consumer;

/** {@code POST /internal/openai/v1/chat/completions}. */
public interface ChatFacadeUseCase {

    FacadeResult complete(FacadeCommand command);

    /** Streams unmasked text deltas; tool calls come whole in the result. */
    FacadeResult stream(FacadeCommand command, Consumer<String> deltas);
}
