package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.AiProvider;
import java.util.function.Consumer;

/** A chat provider (Groq, Ollama) behind Spring AI. Methods throw {@link ProviderFailure}. */
public interface ChatProviderPort {

    AiProvider provider();

    ChatResult call(ChatRequest request);

    /** Streams text deltas to {@code onDelta} and returns the whole answer with its usage at the end. */
    ChatResult stream(ChatRequest request, Consumer<String> onDelta);
}
