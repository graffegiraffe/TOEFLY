package com.toefly.tutoragent.application.port.out;

/** The active version of a prompt from the registry of ai-gateway ({@code GET /internal/v1/prompts/{key}}). */
public interface PromptPort {

    Prompt active(String promptKey);

    record Prompt(String key, int version, String system, String user) {}
}
