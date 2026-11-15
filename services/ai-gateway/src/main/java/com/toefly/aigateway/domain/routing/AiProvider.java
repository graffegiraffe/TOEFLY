package com.toefly.aigateway.domain.routing;

/** Providers behind the gateway (canon §5.4). */
public enum AiProvider {
    GROQ,
    AZURE_SPEECH,
    OLLAMA;

    /** Text leaves the project's infrastructure only for these providers; it must be masked first (ADR-0013). */
    public boolean external() {
        return this != OLLAMA;
    }
}
