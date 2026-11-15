package com.toefly.aigateway.application.port.in;

import java.util.function.Consumer;

/** {@code POST /internal/v1/ai/chat/completions} and {@code /chat/streams}. */
public interface CompleteUseCase {

    CompletionResult complete(CompletionCommand command);

    /** Streams unmasked text deltas; a structured prompt cannot be streamed. */
    CompletionResult stream(CompletionCommand command, Consumer<String> deltas);
}
