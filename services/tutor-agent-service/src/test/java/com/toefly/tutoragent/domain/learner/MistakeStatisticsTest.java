package com.toefly.tutoragent.domain.learner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MistakeStatisticsTest {

    static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    static final UUID USER = UUID.randomUUID();
    static final Taxonomy TAXONOMY = new Taxonomy(Map.of(
            "GRAMMAR.ARTICLES",
            new Taxonomy.Entry("GRAMMAR.ARTICLES", "Articles", "Артикли", "ROUNDUP5.U10", "Round-Up Unit 10"),
            "SPELLING.COMMON",
            new Taxonomy.Entry("SPELLING.COMMON", "Spelling", "Орфография", null, null)));

    static MistakeRecord record(String code, Severity severity, Instant at, UUID source) {
        return new MistakeRecord(
                UUID.randomUUID(),
                USER,
                code,
                SourceType.WRITING,
                source,
                null,
                "WRITE_EMAIL",
                "in library",
                "in the library",
                "Use the article.",
                severity,
                List.of(),
                at);
    }

    @Test
    @DisplayName("a weight halves every 21 days and depends on the severity (docs/06 §6.2)")
    void weightDecays() {
        UUID source = UUID.randomUUID();
        List<MistakeStat> stats = MistakeStatistics.compute(
                List.of(
                        record("GRAMMAR.ARTICLES", Severity.MAJOR, NOW, source),
                        record("GRAMMAR.ARTICLES", Severity.CRITICAL, NOW.minus(Duration.ofDays(21)), source)),
                List.of(),
                TAXONOMY,
                NOW);
        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().weight()).isCloseTo(1.0 + 0.75, within(0.001));
        assertThat(stats.getFirst().unitRefs()).containsExactly("ROUNDUP5.U10");
    }

    @Test
    @DisplayName("a frequent heavy subcategory is ACTIVE and ranked above a rare light one")
    void activeOutranksWatch() {
        List<MistakeRecord> records = new ArrayList<>();
        List<Opportunity> opportunities = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID source = UUID.randomUUID();
            records.add(record("GRAMMAR.ARTICLES", Severity.MAJOR, NOW.minus(Duration.ofDays(i)), source));
            opportunities.add(new Opportunity("GRAMMAR", source, null, NOW.minus(Duration.ofDays(i))));
        }
        records.add(record("SPELLING.COMMON", Severity.MINOR, NOW, UUID.randomUUID()));
        List<MistakeStat> stats = MistakeStatistics.compute(records, opportunities, TAXONOMY, NOW);
        assertThat(stats).extracting(MistakeStat::subcategory).containsExactly("GRAMMAR.ARTICLES", "SPELLING.COMMON");
        assertThat(stats.getFirst().status()).isEqualTo(MistakeStatus.ACTIVE);
        assertThat(stats.getFirst().errorRate()).isEqualTo(1.0);
        assertThat(stats.getFirst().occurrencesLast7Days()).isEqualTo(4);
        assertThat(stats.get(1).status()).isEqualTo(MistakeStatus.WATCH);
    }

    @Test
    @DisplayName("three clean opportunities after an old light mistake make it RESOLVED")
    void resolvesAfterThreeCleanOpportunities() {
        UUID old = UUID.randomUUID();
        List<Opportunity> opportunities =
                new ArrayList<>(List.of(new Opportunity("GRAMMAR", old, null, NOW.minus(Duration.ofDays(40)))));
        for (int i = 1; i <= 3; i++) {
            opportunities.add(new Opportunity("GRAMMAR", UUID.randomUUID(), null, NOW.minus(Duration.ofDays(i))));
        }
        List<MistakeStat> stats = MistakeStatistics.compute(
                List.of(record("GRAMMAR.ARTICLES", Severity.MINOR, NOW.minus(Duration.ofDays(40)), old)),
                opportunities,
                TAXONOMY,
                NOW);
        assertThat(stats.getFirst().status()).isEqualTo(MistakeStatus.RESOLVED);
        assertThat(stats.getFirst().priority()).isLessThan(stats.getFirst().weight() * 0.6);
    }

    @Test
    @DisplayName("fewer mistakes per opportunity in the last 14 days is IMPROVING, more is WORSENING")
    void trend() {
        List<MistakeRecord> records = new ArrayList<>();
        List<Opportunity> opportunities = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID source = UUID.randomUUID();
            Instant at = NOW.minus(Duration.ofDays(20 + i));
            records.add(record("GRAMMAR.ARTICLES", Severity.MAJOR, at, source));
            opportunities.add(new Opportunity("GRAMMAR", source, null, at));
        }
        for (int i = 0; i < 4; i++) {
            opportunities.add(new Opportunity("GRAMMAR", UUID.randomUUID(), null, NOW.minus(Duration.ofDays(i))));
        }
        records.add(record(
                "GRAMMAR.ARTICLES", Severity.MAJOR, NOW, opportunities.getLast().sourceId()));
        assertThat(MistakeStatistics.compute(records, opportunities, TAXONOMY, NOW)
                        .getFirst()
                        .trend())
                .isEqualTo(MistakeTrend.IMPROVING);
        List<MistakeRecord> recent = List.of(record("GRAMMAR.ARTICLES", Severity.MAJOR, NOW, UUID.randomUUID()));
        assertThat(MistakeStatistics.compute(recent, List.of(), TAXONOMY, NOW)
                        .getFirst()
                        .trend())
                .isEqualTo(MistakeTrend.WORSENING);
    }

    @Test
    @DisplayName("the learner model keeps the language and goal and lists unresolved top mistakes")
    void learnerModel() {
        LearnerModel model = LearnerModel.empty(USER)
                .withGoal(4.5, java.time.LocalDate.parse("2027-01-20"), "Europe/Minsk", ExplanationLanguage.RU, NOW);
        assertThat(model.version()).isEqualTo(1);
        assertThat(model.language()).isEqualTo(ExplanationLanguage.RU);
        LearnerModel updated = model.withStats(
                        MistakeStatistics.compute(
                                List.of(record("GRAMMAR.ARTICLES", Severity.MAJOR, NOW, UUID.randomUUID())),
                                List.of(),
                                TAXONOMY,
                                NOW),
                        NOW)
                .withBands(Map.of("OVERALL", 4.0), NOW)
                .withLanguage(ExplanationLanguage.EN, NOW);
        assertThat(updated.version()).isEqualTo(4);
        assertThat(updated.topMistakes(3)).hasSize(1);
        assertThat(updated.bands()).containsEntry("OVERALL", 4.0);
        assertThat(TAXONOMY.title("GRAMMAR.ARTICLES", ExplanationLanguage.RU)).isEqualTo("Артикли");
        assertThat(TAXONOMY.normalize("GRAMMAR.NOPE")).isEqualTo("GRAMMAR.OTHER");
        assertThat(TAXONOMY.unitTitle("ROUNDUP5.U10")).isEqualTo("Round-Up Unit 10");
    }

    @Test
    @DisplayName("the memory text is Wrong | Right | Rule and at most 400 characters (docs/06 §6.4)")
    void memoryText() {
        MistakeRecord r = record("GRAMMAR.ARTICLES", Severity.MAJOR, NOW, UUID.randomUUID());
        assertThat(r.memoryText()).isEqualTo("Wrong: in library | Right: in the library | Rule: Use the article.");
        assertThat(r.category()).isEqualTo(MistakeCategory.GRAMMAR);
        assertThat(Severity.MINOR.memorable()).isFalse();
    }
}
