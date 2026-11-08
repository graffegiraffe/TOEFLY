package com.toefly.content.application.port.out;

import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.seed.SeedUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface UnitStorePort {

    /** The stored content hash of a unit, if it exists. */
    Optional<String> contentHash(SeedUnit.Kind kind, UUID id);

    void insert(SeedUnit unit, UUID sourceId, String status);

    /** Replaces content, keeps status. */
    void updateContent(SeedUnit unit);

    List<UnitView> list(SeedUnit.Kind kind, boolean approvedOnly, @Nullable String framework);

    Optional<UnitView> find(SeedUnit.Kind kind, UUID id);

    boolean approve(SeedUnit.Kind kind, UUID id);
}
