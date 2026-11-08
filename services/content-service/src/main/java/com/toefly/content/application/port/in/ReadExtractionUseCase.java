package com.toefly.content.application.port.in;

import com.toefly.content.application.seed.Extraction;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Administrator view of how seed objects were extracted; ids without metadata are absent from the result. */
public interface ReadExtractionUseCase {

    Map<UUID, Extraction> extractions(Collection<UUID> ids);
}
