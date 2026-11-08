package com.toefly.content.adapter.in.web;

import com.toefly.content.adapter.in.web.api.AdminUnitsApi;
import com.toefly.content.adapter.in.web.api.UnitsApi;
import com.toefly.content.adapter.in.web.dto.GrammarUnit;
import com.toefly.content.adapter.in.web.dto.GrammarUnitList;
import com.toefly.content.adapter.in.web.dto.ItemStatus;
import com.toefly.content.adapter.in.web.dto.VocabularyUnit;
import com.toefly.content.adapter.in.web.dto.VocabularyUnitList;
import com.toefly.content.adapter.in.web.dto.VocabularyWord;
import com.toefly.content.application.port.in.BrowseUnitsUseCase;
import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.seed.SeedUnit;
import com.toefly.content.domain.catalog.UnitNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class UnitsController implements UnitsApi, AdminUnitsApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final BrowseUnitsUseCase units;

    public UnitsController(BrowseUnitsUseCase units) {
        this.units = units;
    }

    @Override
    public ResponseEntity<GrammarUnitList> listGrammarUnits(@Nullable String framework) {
        return ResponseEntity.ok(grammarList(units.list(SeedUnit.Kind.GRAMMAR_UNIT, framework)));
    }

    @Override
    public ResponseEntity<GrammarUnit> getGrammarUnit(UUID unitId) {
        return ResponseEntity.ok(grammar(find(units.approved(SeedUnit.Kind.GRAMMAR_UNIT, unitId), unitId)));
    }

    @Override
    public ResponseEntity<VocabularyUnitList> listVocabularyUnits() {
        return ResponseEntity.ok(vocabularyList(units.list(SeedUnit.Kind.VOCABULARY_UNIT, null)));
    }

    @Override
    public ResponseEntity<VocabularyUnit> getVocabularyUnit(UUID unitId) {
        return ResponseEntity.ok(vocabulary(find(units.approved(SeedUnit.Kind.VOCABULARY_UNIT, unitId), unitId)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrammarUnitList> adminListGrammarUnits() {
        return ResponseEntity.ok(grammarList(units.listAll(SeedUnit.Kind.GRAMMAR_UNIT)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VocabularyUnitList> adminListVocabularyUnits() {
        return ResponseEntity.ok(vocabularyList(units.listAll(SeedUnit.Kind.VOCABULARY_UNIT)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrammarUnit> adminApproveGrammarUnit(UUID unitId) {
        if (!units.approve(SeedUnit.Kind.GRAMMAR_UNIT, unitId)) {
            throw new UnitNotFoundException(unitId);
        }
        return ResponseEntity.ok(grammar(find(units.any(SeedUnit.Kind.GRAMMAR_UNIT, unitId), unitId)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VocabularyUnit> adminApproveVocabularyUnit(UUID unitId) {
        if (!units.approve(SeedUnit.Kind.VOCABULARY_UNIT, unitId)) {
            throw new UnitNotFoundException(unitId);
        }
        return ResponseEntity.ok(vocabulary(find(units.any(SeedUnit.Kind.VOCABULARY_UNIT, unitId), unitId)));
    }

    private static UnitView find(Optional<UnitView> unit, UUID id) {
        return unit.orElseThrow(() -> new UnitNotFoundException(id));
    }

    private static GrammarUnitList grammarList(List<UnitView> views) {
        return new GrammarUnitList()
                .items(views.stream().map(UnitsController::grammar).toList());
    }

    private static VocabularyUnitList vocabularyList(List<UnitView> views) {
        return new VocabularyUnitList()
                .items(views.stream().map(UnitsController::vocabulary).toList());
    }

    private static GrammarUnit grammar(UnitView u) {
        return new GrammarUnit()
                .id(u.id())
                .framework(u.framework())
                .unitNo(u.unitNo())
                .title(u.title())
                .status(ItemStatus.fromValue(u.status()))
                .sourceCode(u.sourceCode())
                .topicCodes(u.topicCodes())
                .progressCheckNo(u.progressCheckNo())
                .ruleCard(u.ruleCardJson() == null ? null : JSON.readValue(u.ruleCardJson(), MAP))
                .exercises(JSON.readValue(u.exercisesJson(), LIST));
    }

    private static VocabularyUnit vocabulary(UnitView u) {
        Function<Map<String, Object>, VocabularyWord> word = m -> new VocabularyWord()
                .word((String) m.get("word"))
                .pos((String) m.get("pos"))
                .definition((String) m.get("definition"))
                .example((String) m.get("example"));
        return new VocabularyUnit()
                .id(u.id())
                .unitNo(u.unitNo())
                .theme(u.theme())
                .title(u.title())
                .status(ItemStatus.fromValue(u.status()))
                .sourceCode(u.sourceCode())
                .words(JSON.readValue(u.wordsJson(), LIST).stream().map(word).toList())
                .exercises(JSON.readValue(u.exercisesJson(), LIST));
    }
}
