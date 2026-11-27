package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeCategory;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.MistakeStat;
import com.toefly.tutoragent.domain.learner.MistakeStatus;
import com.toefly.tutoragent.domain.learner.MistakeTrend;
import com.toefly.tutoragent.domain.learner.Severity;
import com.toefly.tutoragent.domain.learner.SourceType;
import com.toefly.tutoragent.domain.review.ReviewValidator;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** Live evals of WeeklyReviewer (`weekly-review`, docs/06 §5.3): numbers checked by the validator, style by the judge. */
@EnabledIfSystemProperty(named = "evals.live", matches = "true")
class WeeklyReviewLiveEvalTest {

    static final List<String> PROMPTS = List.of("tutor.weekly-review", "tutor.eval.judge");
    static final Instant CLOSED = Instant.parse("2026-11-22T10:00:00Z");

    @Test
    void runWeeklyReview() throws Exception {
        TutorEvals live = new TutorEvals();
        AiPort ai = live.ai();
        JsonNode suite = TutorEvals.read(Path.of("src/evals/resources/weekly-review.json"));
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode c : suite.path("cases")) {
            results.add(run(c, live, ai));
            System.out.println("case " + c.path("id").asString() + ": " + results.getLast());
        }
        List<JsonNode> nodes = results.stream().map(r -> (JsonNode) TutorEvals.JSON.valueToTree(r)).toList();
        Map<String, Double> metrics = TutorEvalMetrics.review(nodes);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "weekly-review");
        report.put("runAt", Instant.now().toString());
        report.put("promptSha256", TutorEvals.promptHashes(PROMPTS));
        report.put("metrics", metrics);
        report.put("misses", TutorEvalMetrics.misses(metrics, TutorEvalMetrics.REVIEW_THRESHOLDS));
        report.put("cases", results);
        Files.createDirectories(TutorEvals.REPORTS);
        Files.writeString(
                TutorEvals.REPORTS.resolve("weekly-review.json"),
                TutorEvals.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        System.out.println("weekly-review metrics " + metrics + " misses " + report.get("misses"));
        assertThat(results).isNotEmpty();
    }

    Map<String, Object> run(JsonNode c, TutorEvals live, AiPort ai) {
        UUID user = TutorEvals.id(c.path("id").asString());
        Clock clock = Clock.fixed(CLOSED.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        Fakes.Models models = new Fakes.Models();
        Fakes.Mistakes mistakes = new Fakes.Mistakes();
        Fakes.Results results = new Fakes.Results();
        Fakes.Reviews reviews = new Fakes.Reviews();
        ExplanationLanguage language = ExplanationLanguage.of(c.path("language").asString());
        List<MistakeStat> stats = new ArrayList<>();
        for (JsonNode m : c.path("mistakes")) {
            String code = m.path("subcategory").asString();
            for (int i = 0; i < m.path("count").asInt(); i++) {
                mistakes.records.add(new MistakeRecord(UUID.randomUUID(), user, code, SourceType.WRITING, UUID.randomUUID(), null,
                        "WRITE_EMAIL", m.path("wrong").asString(), m.path("right").asString(), null, Severity.MAJOR,
                        Fakes.taxonomy().unitRefs(code), CLOSED.minus(Duration.ofDays(1 + i))));
            }
            stats.add(new MistakeStat(code, MistakeCategory.ofSubcategory(code), m.path("count").asInt(), 0.4, MistakeTrend.STABLE,
                    MistakeStatus.ACTIVE, m.path("count").asInt(), m.path("count").asInt(), m.path("count").asInt(), 8, CLOSED,
                    Fakes.taxonomy().unitRefs(code)));
        }
        Map<String, Double> bands = TutorEvals.JSON.convertValue(c.path("bands"), new TypeReference<Map<String, Double>>() {});
        Map<String, Double> previous = TutorEvals.JSON.convertValue(c.path("previousBands"), new TypeReference<Map<String, Double>>() {});
        int weekNo = c.path("weekNo").asInt();
        models.save(new LearnerModel(user, 1, CLOSED, language, 4.5, null, "UTC", bands, stats));
        models.snapshots.put(user + ":" + (weekNo - 1), new LearnerModel(user, 1, CLOSED, language, 4.5, null, "UTC", previous, List.of()));
        for (JsonNode s : c.path("scores")) {
            results.save(new PracticeResultRepository.Result(UUID.randomUUID(), user, s.path("taskType").asString(),
                    s.path("accuracy").isNumber() ? s.path("accuracy").asDouble() : null,
                    s.path("score").isNumber() ? s.path("score").asDouble() : null, CLOSED.minus(Duration.ofDays(2))));
        }
        LearnerModelService learner = new LearnerModelService(
                models, mistakes, new Fakes.Memory(), new Fakes.Publications(), new Fakes.LearnerEvents(), results,
                new Fakes.Practice(), new Fakes.Assessment(), Fakes.taxonomy(), Fakes.TX, clock) {
            @Override
            public void weekCompleted(UUID userId, int no) {}
        };
        WeeklyReviewService service = new WeeklyReviewService(learner, models, mistakes, results, reviews, new Fakes.DeferredRows(),
                new Fakes.Rights(), ai, new Fakes.ReviewEvents(), Fakes.taxonomy(), Fakes.TX, clock);
        List<ClosedWeek.Section> sections = new ArrayList<>();
        for (JsonNode s : c.path("sections")) {
            sections.add(new ClosedWeek.Section(s.path("section").asString(), s.path("tasksTotal").asInt(), s.path("tasksDone").asInt(),
                    s.path("minutesPlanned").asInt(), s.path("minutesDone").asInt()));
        }
        int total = c.path("tasksTotal").asInt();
        int done = c.path("tasksDone").asInt();
        ClosedWeek week = new ClosedWeek(user, UUID.randomUUID(), weekNo, LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-21"),
                CLOSED, total, done, done * 100.0 / total, sections, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.path("id").asString());
        try {
            service.weekCompleted(week);
            WeeklyReview review = reviews.findByWeek(week.weekId()).orElseThrow();
            result.put("aiFallback", review.aiFallback());
            result.put("violations", ReviewValidator.violations(review.text(), review.facts()).size());
            result.put("headline", review.text().headline());
            String text = review.text().summary() + "\nTop mistakes: " + review.text().topMistakes().stream()
                    .map(m -> m.pattern() + " — " + m.advice()).toList() + "\nStrengths: " + review.text().strengths()
                    + "\nFocus: " + review.text().nextWeekFocus().stream().map(WeeklyReview.Focus::why).toList()
                    + "\n" + review.text().motivation();
            result.put("labels", live.judge(ai, "A weekly review of a TOEFL student. Facts: " + c, text));
        } catch (RuntimeException failure) {
            result.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            result.put("aiFallback", true);
        }
        return result;
    }
}
