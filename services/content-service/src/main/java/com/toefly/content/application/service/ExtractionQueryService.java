package com.toefly.content.application.service;

import com.toefly.content.application.port.in.ReadExtractionUseCase;
import com.toefly.content.application.port.out.ExtractionStorePort;
import com.toefly.content.application.seed.Extraction;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExtractionQueryService implements ReadExtractionUseCase {

    private final ExtractionStorePort store;

    public ExtractionQueryService(ExtractionStorePort store) {
        this.store = store;
    }

    @Override
    public Map<UUID, Extraction> extractions(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : store.find(ids);
    }
}
