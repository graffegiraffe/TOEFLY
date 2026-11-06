package com.toefly.content.application.port.out;

import com.toefly.content.domain.catalog.Source;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceStorePort {

    /** Inserts the source or updates its descriptive fields; the {@code id} of an existing code wins. */
    Source upsert(Source source);

    Optional<Source> findByCode(String code);

    Optional<Source> findById(UUID id);

    List<Source> findAll();
}
