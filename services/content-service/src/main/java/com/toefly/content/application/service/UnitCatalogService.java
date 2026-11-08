package com.toefly.content.application.service;

import com.toefly.content.application.port.in.BrowseUnitsUseCase;
import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.port.out.UnitStorePort;
import com.toefly.content.application.seed.SeedUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class UnitCatalogService implements BrowseUnitsUseCase {

    private final UnitStorePort units;

    public UnitCatalogService(UnitStorePort units) {
        this.units = units;
    }

    @Override
    public List<UnitView> list(SeedUnit.Kind kind, @Nullable String framework) {
        return units.list(kind, true, framework);
    }

    @Override
    public List<UnitView> listAll(SeedUnit.Kind kind) {
        return units.list(kind, false, null);
    }

    @Override
    public Optional<UnitView> approved(SeedUnit.Kind kind, UUID id) {
        return units.find(kind, id).filter(u -> "APPROVED".equals(u.status()));
    }

    @Override
    public Optional<UnitView> any(SeedUnit.Kind kind, UUID id) {
        return units.find(kind, id);
    }

    @Override
    public boolean approve(SeedUnit.Kind kind, UUID id) {
        return units.approve(kind, id);
    }
}
