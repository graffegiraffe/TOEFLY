package com.toefly.content.adapter.in.web;

import com.toefly.content.adapter.in.web.api.InternalApi;
import com.toefly.content.adapter.in.web.dto.GrammarUnitHit;
import com.toefly.content.adapter.in.web.dto.GrammarUnitHits;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.adapter.in.web.dto.ItemSetHitSummary;
import com.toefly.content.adapter.in.web.dto.ItemSetSearchHits;
import com.toefly.content.adapter.in.web.dto.ItemSetSearchQuery;
import com.toefly.content.adapter.in.web.dto.TaskType;
import com.toefly.content.application.port.in.BrowseCatalogUseCase;
import com.toefly.content.application.port.in.TutorSearchUseCase;
import com.toefly.content.domain.catalog.ItemSetNotFoundException;
import com.toefly.content.domain.catalog.RelativeDifficulty;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service view with answer keys, for practice-service (docs/04-architecture.md §3.4), and the catalog
 * searches of the tutor agent (docs/06-ai-agent.md §4.5).
 */
@RestController
public class InternalController implements InternalApi {

    /** Planned time of a grammar unit, as the week composition of planning-service counts it. */
    static final double UNIT_MINUTES = 60;

    private final BrowseCatalogUseCase catalog;
    private final TutorSearchUseCase search;

    public InternalController(BrowseCatalogUseCase catalog, TutorSearchUseCase search) {
        this.catalog = catalog;
        this.search = search;
    }

    @Override
    public ResponseEntity<ItemSetSearchHits> searchItemSetsInternal(ItemSetSearchQuery query) {
        var rows = search.searchItemSets(new TutorSearchUseCase.ItemSetQuery(
                com.toefly.content.domain.catalog.TaskType.valueOf(
                        query.getTaskType().name()),
                query.getSkillFocus(),
                query.getTopic(),
                query.getDifficulty() == null
                        ? RelativeDifficulty.SAME
                        : RelativeDifficulty.valueOf(query.getDifficulty().name()),
                query.getReferenceB() == null ? null : query.getReferenceB().doubleValue(),
                query.getExcludeItemSetIds() == null ? java.util.List.of() : query.getExcludeItemSetIds(),
                query.getLimit()));
        return ResponseEntity.ok(new ItemSetSearchHits()
                .items(rows.stream()
                        .map(row -> {
                            var s = row.itemSet();
                            return new ItemSetHitSummary()
                                    .itemSetId(s.id())
                                    .taskType(TaskType.fromValue(s.taskType().name()))
                                    .title(CatalogMapper.stimulusTitle(s.payload(), s.topic()))
                                    .topic(s.topic())
                                    .estimatedMinutes(BigDecimal.valueOf(s.estimatedMinutes()))
                                    .difficultyB(BigDecimal.valueOf(s.bMean()))
                                    .skills(s.skills());
                        })
                        .toList()));
    }

    @Override
    public ResponseEntity<GrammarUnitHits> searchGrammarUnitsInternal(
            @Nullable String topic, @Nullable String subcategory, Integer limit) {
        return ResponseEntity.ok(new GrammarUnitHits()
                .units(search.searchGrammarUnits(topic, subcategory, limit).stream()
                        .map(match -> new GrammarUnitHit()
                                .unitId(match.unit().id())
                                .code(match.code().code())
                                .title(match.unit().title())
                                .source(match.sourceTitle())
                                .estimatedMinutes(BigDecimal.valueOf(UNIT_MINUTES))
                                .summary(CatalogMapper.ruleSummary(match.unit().ruleCardJson())))
                        .toList()));
    }

    @Override
    public ResponseEntity<ItemSet> internalGetItemSet(UUID itemSetId) {
        var set = catalog.any(itemSetId).orElseThrow(() -> new ItemSetNotFoundException(itemSetId));
        return ResponseEntity.ok(CatalogMapper.full(set, catalog.source(set.sourceId()), true));
    }
}
