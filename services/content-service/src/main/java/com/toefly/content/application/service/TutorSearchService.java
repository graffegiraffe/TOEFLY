package com.toefly.content.application.service;

import com.toefly.content.application.port.in.BrowseCatalogUseCase.Row;
import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.port.in.TutorSearchUseCase;
import com.toefly.content.application.port.out.CandidateQuery;
import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.application.port.out.UnitStorePort;
import com.toefly.content.application.seed.SeedUnit;
import com.toefly.content.domain.catalog.GrammarUnitCode;
import com.toefly.content.domain.catalog.MistakeTaxonomy;
import com.toefly.content.domain.catalog.Source;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Catalog searches for the tools of the tutor agent; answers from content_db only (docs/05 §9.4). */
@Service
public class TutorSearchService implements TutorSearchUseCase {

    /** Words of a topic that say nothing about a grammar unit. */
    private static final Set<String> STOP_WORDS =
            Set.of("and", "the", "for", "with", "vs", "versus", "rule", "rules", "use", "using", "of", "in");

    private final CatalogQueryPort catalog;
    private final UnitStorePort units;
    private final SourceStorePort sources;
    private final MistakeTaxonomy taxonomy;

    public TutorSearchService(
            CatalogQueryPort catalog, UnitStorePort units, SourceStorePort sources, MistakeTaxonomy taxonomy) {
        this.catalog = catalog;
        this.units = units;
        this.sources = sources;
        this.taxonomy = taxonomy;
    }

    @Override
    public List<Row> searchItemSets(ItemSetQuery query) {
        String skill = blankToNull(query.skillFocus());
        String topic = blankToNull(query.topic());
        return catalog.candidates(new CandidateQuery(
                query.taskType(),
                skill == null ? null : skill.toLowerCase(Locale.ROOT),
                skill == null ? null : subskill(skill),
                topic,
                query.referenceB() == null ? null : query.difficulty().around(query.referenceB()),
                query.excludeItemSetIds(),
                clamp(query.limit(), DEFAULT_SETS, MAX_SETS)));
    }

    @Override
    public List<UnitMatch> searchGrammarUnits(
            @Nullable String topic, @Nullable String subcategory, @Nullable Integer limit) {
        String text = blankToNull(topic);
        List<GrammarUnitCode> wanted = new ArrayList<>();
        if (subcategory != null) {
            taxonomy.unitOf(subcategory).ifPresent(wanted::add);
        }
        if (text != null && taxonomy.isSubcategory(text)) {
            taxonomy.unitOf(text).ifPresent(wanted::add);
            text = null;
        }
        if (text != null) {
            GrammarUnitCode.parse(text).ifPresent(wanted::add);
        }
        List<UnitView> approved = units.list(SeedUnit.Kind.GRAMMAR_UNIT, true, null);
        Map<UUID, UnitView> found = new LinkedHashMap<>();
        for (GrammarUnitCode code : wanted) {
            approved.stream().filter(u -> codeOf(u).equals(Optional.of(code))).forEach(u -> found.put(u.id(), u));
        }
        if (text != null) {
            List<String> words = words(text);
            approved.stream()
                    .filter(u -> score(u, words) > 0)
                    .sorted(Comparator.comparingInt((UnitView u) -> -score(u, words)))
                    .forEach(u -> found.putIfAbsent(u.id(), u));
        }
        Map<String, String> titles = new LinkedHashMap<>();
        return found.values().stream()
                .limit(clamp(limit, DEFAULT_UNITS, MAX_UNITS))
                .map(u -> new UnitMatch(
                        u,
                        codeOf(u).orElseThrow(),
                        titles.computeIfAbsent(
                                u.sourceCode(),
                                c -> sources.findByCode(c).map(Source::title).orElse(c))))
                .toList();
    }

    /** {@code READING.INFERENCE} → {@code INFERENCE}; a tag without a dot is a subskill itself. */
    static String subskill(String skill) {
        int dot = skill.lastIndexOf('.');
        return skill.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    static int clamp(@Nullable Integer limit, int fallback, int max) {
        return limit == null ? fallback : Math.max(1, Math.min(max, limit));
    }

    private static Optional<GrammarUnitCode> codeOf(UnitView unit) {
        try {
            return Optional.of(new GrammarUnitCode(unit.framework(), unit.unitNo()));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return Optional.empty();
        }
    }

    /** How many words of the topic the title or the topic codes of a unit contain; units without a code score 0. */
    static int score(UnitView unit, List<String> words) {
        if (codeOf(unit).isEmpty()) {
            return 0;
        }
        String haystack = (unit.title() + " " + String.join(" ", unit.topicCodes()))
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ');
        return (int) words.stream().filter(haystack::contains).count();
    }

    static List<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w))
                .distinct()
                .toList();
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
