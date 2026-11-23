package com.toefly.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.port.in.TutorSearchUseCase.ItemSetQuery;
import com.toefly.content.application.port.in.TutorSearchUseCase.UnitMatch;
import com.toefly.content.application.port.out.CandidateQuery;
import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.application.port.out.UnitStorePort;
import com.toefly.content.application.seed.SeedUnit;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.MistakeTaxonomy;
import com.toefly.content.domain.catalog.RelativeDifficulty;
import com.toefly.content.domain.catalog.Source;
import com.toefly.content.domain.catalog.TaskType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("searches for the tutor agent (docs/12 phase 5c, decision 2)")
class TutorSearchServiceTest {

    private static final UUID EXCLUDED = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private final CatalogQueryPort catalog = mock(CatalogQueryPort.class);
    private final UnitStorePort units = mock(UnitStorePort.class);
    private final SourceStorePort sources = mock(SourceStorePort.class);
    private final TutorSearchService service = new TutorSearchService(
            catalog,
            units,
            sources,
            new MistakeTaxonomy(Map.of(
                    "GRAMMAR.ARTICLES", "ROUNDUP5.U10",
                    "GRAMMAR.VERB_TENSES", "ROUNDUP5.U01",
                    "GRAMMAR.CLAUSES_LINKING", "COLLINS_GR.U07",
                    "VOCABULARY.COLLOCATION", "COLLINS_VOC.U08")));

    private CandidateQuery searched(ItemSetQuery query) {
        when(catalog.candidates(any())).thenReturn(List.of());
        service.searchItemSets(query);
        ArgumentCaptor<CandidateQuery> captor = ArgumentCaptor.forClass(CandidateQuery.class);
        verify(catalog).candidates(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName(
            "a subcategory becomes a lower-case skill tag and an upper-case item subskill; easier is below b − 0.5")
    void itemSetQuery() {
        CandidateQuery q = searched(new ItemSetQuery(
                TaskType.READ_ACADEMIC_PASSAGE,
                " READING.INFERENCE ",
                " plants ",
                RelativeDifficulty.EASIER,
                0.4,
                List.of(EXCLUDED),
                9));

        assertThat(q.taskType()).isEqualTo(TaskType.READ_ACADEMIC_PASSAGE);
        assertThat(q.skill()).isEqualTo("reading.inference");
        assertThat(q.subskill()).isEqualTo("INFERENCE");
        assertThat(q.topic()).isEqualTo("plants");
        assertThat(q.band()).isNotNull();
        assertThat(q.band().max()).isEqualTo(0.4 - 0.5);
        assertThat(q.band().contains(-0.11)).isTrue();
        assertThat(q.band().contains(-0.09)).isFalse();
        assertThat(q.excludeItemSetIds()).containsExactly(EXCLUDED);
        assertThat(q.limit()).isEqualTo(5);
    }

    @Test
    @DisplayName("without a reference b there is no band; blank texts are ignored and the limit defaults to 3")
    void defaults() {
        CandidateQuery q = searched(
                new ItemSetQuery(TaskType.WRITE_EMAIL, " ", null, RelativeDifficulty.HARDER, null, List.of(), null));

        assertThat(q.skill()).isNull();
        assertThat(q.subskill()).isNull();
        assertThat(q.topic()).isNull();
        assertThat(q.band()).isNull();
        assertThat(q.limit()).isEqualTo(3);
        assertThat(TutorSearchService.clamp(0, 3, 5)).isEqualTo(1);
        assertThat(TutorSearchService.subskill("detail")).isEqualTo("DETAIL");
    }

    private static UnitView unit(String framework, int no, String title, String... topics) {
        return new UnitView(
                UUID.nameUUIDFromBytes((framework + no).getBytes()),
                SeedUnit.Kind.GRAMMAR_UNIT,
                "round-up-5",
                no,
                title,
                "APPROVED",
                framework,
                null,
                List.of(topics),
                null,
                null,
                null,
                "[]");
    }

    private void catalogOfUnits() {
        when(units.list(SeedUnit.Kind.GRAMMAR_UNIT, true, null))
                .thenReturn(List.of(
                        unit("ROUND_UP_5", 1, "Present forms", "present-forms"),
                        unit("ROUND_UP_5", 2, "Past forms", "past-forms"),
                        unit("ROUND_UP_5", 10, "Nouns – Articles"),
                        unit("COLLINS_GRAMMAR", 7, "Linking words"),
                        unit("OTHER_BOOK", 3, "Present and past")));
        when(sources.findByCode("round-up-5"))
                .thenReturn(Optional.of(new Source(
                        UUID.randomUUID(),
                        "round-up-5",
                        "New Round-Up 5",
                        "Pearson",
                        2010,
                        null,
                        ExamFormat.TOEFL_2026,
                        "attribution",
                        "{}")));
    }

    @Test
    @DisplayName("a subcategory finds its unit through the taxonomy; the source title comes along")
    void unitBySubcategory() {
        catalogOfUnits();

        List<UnitMatch> found = service.searchGrammarUnits(null, "grammar.articles", null);

        assertThat(found).singleElement().satisfies(m -> {
            assertThat(m.code().code()).isEqualTo("ROUNDUP5.U10");
            assertThat(m.unit().title()).isEqualTo("Nouns – Articles");
            assertThat(m.sourceTitle()).isEqualTo("New Round-Up 5");
        });
    }

    @Test
    @DisplayName("the unit of the subcategory comes first, then units by matching words of the topic")
    void unitsByTopic() {
        catalogOfUnits();

        List<UnitMatch> found =
                service.searchGrammarUnits("present perfect vs past simple", "GRAMMAR.CLAUSES_LINKING", 5);

        assertThat(found)
                .extracting(m -> m.code().code())
                .containsExactly("COLLINS_GR.U07", "ROUNDUP5.U01", "ROUNDUP5.U02");
    }

    @Test
    @DisplayName("a taxonomy or unit code given as the topic is resolved; vocabulary codes and nothing find nothing")
    void codesAsTopic() {
        catalogOfUnits();

        assertThat(service.searchGrammarUnits("GRAMMAR.VERB_TENSES", null, null))
                .extracting(m -> m.code().code())
                .containsExactly("ROUNDUP5.U01");
        assertThat(service.searchGrammarUnits("ROUNDUP5.U10", null, null))
                .extracting(m -> m.code().code())
                .containsExactly("ROUNDUP5.U10");
        assertThat(service.searchGrammarUnits(null, "VOCABULARY.COLLOCATION", null))
                .isEmpty();
        assertThat(service.searchGrammarUnits(" ", null, null)).isEmpty();
        assertThat(service.searchGrammarUnits("present", null, 1)).hasSize(1);
    }
}
