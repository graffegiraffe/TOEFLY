package com.toefly.vocabulary.application.port.in;

/** Background enrichment of words saved while ai-gateway was unavailable (docs/04 §3.12). */
public interface EnrichmentUseCase {

    int enrichPending(int limit);
}
