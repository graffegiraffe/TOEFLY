package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.in.MemoryUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fills the embeddings of the mistake memory through ai-gateway (nomic-embed-text, docs/06 §6.4). */
public class MemoryService implements MemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MistakeMemoryRepository memory;
    private final AiPort ai;

    public MemoryService(MistakeMemoryRepository memory, AiPort ai) {
        this.memory = memory;
        this.ai = ai;
    }

    @Override
    public int embedPending(int limit) {
        List<MistakeMemoryRepository.PendingMemory> pending = memory.pending(limit);
        if (pending.isEmpty()) {
            return 0;
        }
        try {
            List<float[]> vectors = ai.embed(
                    pending.stream()
                            .map(MistakeMemoryRepository.PendingMemory::content)
                            .toList(),
                    null);
            for (int i = 0; i < pending.size(); i++) {
                memory.embedded(pending.get(i).recordId(), vectors.get(i));
            }
            return pending.size();
        } catch (AiUnavailableException unavailable) {
            log.warn("Embeddings of {} memory records failed: {}", pending.size(), unavailable.getMessage());
            pending.forEach(p -> memory.failed(p.recordId()));
            return 0;
        }
    }
}
