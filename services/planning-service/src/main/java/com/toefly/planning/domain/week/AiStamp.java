package com.toefly.planning.domain.week;

/** The stamp of an AI result (docs/06-ai-agent.md §2.9), stored with the generated week. */
public record AiStamp(
        String aiCallId,
        String promptKey,
        int promptVersion,
        String provider,
        String model,
        int fallbackDepth,
        boolean degraded) {}
