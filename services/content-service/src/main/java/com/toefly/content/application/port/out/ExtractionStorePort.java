package com.toefly.content.application.port.out;

import com.toefly.content.application.seed.Extraction;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Extraction metadata of item sets and units, keyed by their id. */
public interface ExtractionStorePort {

    void save(UUID targetId, String seedId, Extraction extraction);

    Map<UUID, Extraction> find(Collection<UUID> targetIds);
}
