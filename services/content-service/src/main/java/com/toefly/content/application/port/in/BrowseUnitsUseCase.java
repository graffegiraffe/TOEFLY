package com.toefly.content.application.port.in;

import com.toefly.content.application.seed.SeedUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Grammar and vocabulary units: approved ones for learners, approval by an administrator. */
public interface BrowseUnitsUseCase {

    /** A stored unit as the catalog shows it; the content is kept as JSON text. */
    record UnitView(
            UUID id,
            SeedUnit.Kind kind,
            String sourceCode,
            int unitNo,
            String title,
            String status,
            String framework,
            String theme,
            List<String> topicCodes,
            Integer progressCheckNo,
            String ruleCardJson,
            String wordsJson,
            String exercisesJson) {}

    List<UnitView> list(SeedUnit.Kind kind, @Nullable String framework);

    /** Administrator view: every unit, also those waiting for review. */
    List<UnitView> listAll(SeedUnit.Kind kind);

    /** Learner view: a unit that is not approved does not exist. */
    Optional<UnitView> approved(SeedUnit.Kind kind, UUID id);

    /** @return false if the unit does not exist */
    boolean approve(SeedUnit.Kind kind, UUID id);

    Optional<UnitView> any(SeedUnit.Kind kind, UUID id);
}
