package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import com.toefly.tutoragent.domain.learner.WeekFactsSupport;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolsAndScreensTest {

    final UUID user = UUID.randomUUID();
    final Fakes.Practice practice = new Fakes.Practice();
    final Fakes.Assessment assessment = new Fakes.Assessment();
    final Fakes.Reviews reviews = new Fakes.Reviews();
    final Fakes.Mistakes mistakes = new Fakes.Mistakes();
    final Fakes.Memory memory = new Fakes.Memory();
    final Fakes.Models models = new Fakes.Models();
    final Fakes.Ai ai = new Fakes.Ai();
    final ChatScenarioTest.Owners owners = new ChatScenarioTest.Owners();
    LearnerModelService learner;
    ToolExecutor tools;
    ScreenContextAssembler screens;
    ToolExecutor.ToolContext general;

    @BeforeEach
    void setUp() {
        learner = new LearnerModelService(
                models,
                mistakes,
                memory,
                new Fakes.Publications(),
                new Fakes.LearnerEvents(),
                new Fakes.Results(),
                practice,
                assessment,
                Fakes.taxonomy(),
                Fakes.TX,
                Fakes.clock());
        tools = new ToolExecutor(
                owners, practice, assessment, learner, mistakes, memory, reviews, ai, Fakes.taxonomy(), Fakes.clock());
        screens = new ScreenContextAssembler(practice, assessment, owners, reviews);
        general =
                new ToolExecutor.ToolContext(user, "t", UUID.randomUUID(), UUID.randomUUID(), ChatContextType.GENERAL);
    }

    UUID submission(String status, String versionStatus) {
        UUID id = UUID.randomUUID();
        assessment.submissions.put(
                id,
                new AssessmentPort.Submission(
                        id,
                        user,
                        "WRITE_EMAIL",
                        status,
                        List.of(new AssessmentPort.Version(
                                1,
                                versionStatus,
                                "I study in library.",
                                4,
                                3.0,
                                List.of(new AssessmentPort.Comment(
                                        "in library", "Article.", "in the library", "GRAMMAR", "ARTICLES", "MAJOR")),
                                Fakes.NOW)),
                        "A strong sample."));
        return id;
    }

    @Test
    @DisplayName("read tools answer from owners and local data; unknown or forbidden tools are refused")
    void readTools() {
        assertThat(tools.run("getProgress", Map.of(), general).result()).containsEntry("streak", 3);
        assertThat(tools.run("getCurrentWeek", Map.of(), general).result()).containsKey("weekId");
        assertThat(tools.run("getTimeStats", Map.of("period", "TODAY"), general).result())
                .containsEntry("period", "TODAY");
        assertThat(tools.run("getBandEstimate", Map.of(), general).result()).containsEntry("label", "AI estimate");
        assertThat(tools.run(
                                "searchItems",
                                Map.of("taskType", "WRITE_EMAIL", "topic", "travel", "skillFocus", "X", "limit", 9),
                                general)
                        .status())
                .isEqualTo("OK");
        assertThat(tools.run("explainGrammarRule", Map.of("topic", "GRAMMAR.ARTICLES"), general)
                        .result())
                .containsKey("taxonomyUnits");
        assertThat(tools.run("explainGrammarRule", Map.of("topic", "articles"), general)
                        .status())
                .isEqualTo("OK");
        assertThat(tools.run("nope", Map.of(), general).status()).isEqualTo("DENIED");
        assertThat(tools.run(
                                "getSubmission",
                                Map.of("submissionId", UUID.randomUUID().toString()),
                                new ToolExecutor.ToolContext(
                                        user, null, UUID.randomUUID(), UUID.randomUUID(), ChatContextType.WEEK))
                        .status())
                .isEqualTo("DENIED");
        assertThat(tools.run("getAttemptReview", Map.of("attemptId", "not-an-id"), general)
                        .status())
                .isEqualTo("INVALID_ARGUMENTS");
        assertThat(tools.run(
                                "getAttemptReview",
                                Map.of("attemptId", UUID.randomUUID().toString()),
                                general)
                        .status())
                .isEqualTo("NOT_SUBMITTED");
        assertThat(ToolExecutor.label("getProgress")).isEqualTo("Checking your progress");
        assertThat(ToolExecutor.label("x")).isEqualTo("Working");
    }

    @Test
    @DisplayName("submissions show the model answer only after evaluation (docs/06 §4.2 rule 4)")
    void submissions() {
        UUID evaluated = submission("EVALUATED", "EVALUATED");
        Map<String, Object> out = tools.run(
                        "getSubmission", Map.of("submissionId", evaluated.toString(), "version", 1), general)
                .result();
        assertThat(out).containsEntry("modelAnswer", "A strong sample.");
        UUID draft = submission("DRAFT", "DRAFT");
        assertThat(tools.run("getSubmission", Map.of("submissionId", draft.toString()), general)
                        .status())
                .isEqualTo("NOT_SUBMITTED");
        assertThat(tools.run(
                                "getAttemptReview",
                                Map.of(
                                        "attemptId",
                                        UUID.randomUUID().toString(),
                                        "itemId",
                                        UUID.randomUUID().toString()),
                                general)
                        .status())
                .isEqualTo("NOT_SUBMITTED");
    }

    @Test
    @DisplayName("mistake history comes from the learner model with examples and similar memories")
    void history() {
        learner.submissionEvaluated(user, submission("EVALUATED", "EVALUATED"), 1, Fakes.NOW, Cause.none());
        new MemoryService(memory, ai).embedPending(10);
        Map<String, Object> out = tools.run(
                        "getMistakeHistory",
                        Map.of("category", "grammar", "limit", 3, "sinceWeeks", 2, "query", "in library"),
                        general)
                .result();
        assertThat((List<?>) out.get("mistakes")).hasSize(1);
        assertThat((List<?>) out.get("similar")).hasSize(1);
        assertThat(tools.run("getMistakeHistory", Map.of("subcategory", "SPELLING.COMMON"), general)
                        .result())
                .containsKey("note");
        assertThat(tools.run("getWeeklyReview", Map.of(), general).result()).containsKey("note");
    }

    @Test
    @DisplayName("assignExtraTask proposes a pending action and checks the day")
    void assign() {
        ToolExecutor.Outcome outcome = tools.run(
                "assignExtraTask",
                Map.of("itemSetId", UUID.randomUUID().toString(), "reason", "Practice", "day", "TODAY"),
                general);
        assertThat(outcome.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(outcome.action()).isNotNull();
        assertThat(tools.run(
                                "assignExtraTask",
                                Map.of("itemSetId", UUID.randomUUID().toString(), "day", "SOON"),
                                general)
                        .status())
                .isEqualTo("INVALID_ARGUMENTS");
    }

    @Test
    @DisplayName(
            "screens: a draft becomes SUBMISSION_DRAFT, a review shows text and comments, a week and a review get labels")
    void screens() {
        var draft = screens.load(
                new ChatContext(ChatContextType.SUBMISSION_REVIEW, submission("DRAFT", "DRAFT"), null, null),
                user,
                null);
        assertThat(draft.context().type()).isEqualTo(ChatContextType.SUBMISSION_DRAFT);
        assertThat(draft.draft()).contains("library");
        var review = screens.load(
                new ChatContext(ChatContextType.SUBMISSION_REVIEW, submission("EVALUATED", "EVALUATED"), null, null),
                user,
                null);
        assertThat(review.context().type()).isEqualTo(ChatContextType.SUBMISSION_REVIEW);
        assertThat(review.text()).contains("in the library", "AI estimate 3.0");
        assertThat(review.context().label()).isEqualTo("Review: Write email");
        var foreign = screens.load(
                new ChatContext(ChatContextType.SUBMISSION_REVIEW, submission("EVALUATED", "EVALUATED"), null, null),
                UUID.randomUUID(),
                null);
        assertThat(foreign.context().type()).isEqualTo(ChatContextType.GENERAL);
        var week = screens.load(new ChatContext(ChatContextType.WEEK, null, null, null), user, null);
        assertThat(week.context().label()).isEqualTo("Week 3");
        WeeklyReview stored = WeekFactsSupport.review(user);
        reviews.save(stored);
        var weekly = screens.load(new ChatContext(ChatContextType.WEEKLY_REVIEW, stored.id(), null, null), user, null);
        assertThat(weekly.text()).contains("Weekly review of week 3");
        assertThat(screens.load(new ChatContext(ChatContextType.VOCABULARY, null, null, null), user, null)
                        .text())
                .contains("vocabulary");
        assertThat(screens.load(null, user, null).context().type()).isEqualTo(ChatContextType.GENERAL);
        assertThat(screens.load(new ChatContext(ChatContextType.ATTEMPT_REVIEW, null, null, null), user, null)
                        .context()
                        .type())
                .isEqualTo(ChatContextType.ATTEMPT_IN_PROGRESS);
        UUID attempt = UUID.randomUUID();
        practice.reviews.put(
                attempt,
                new PracticePort.AttemptReview(
                        attempt,
                        "READ_DAILY_LIFE",
                        List.of(new PracticePort.ItemReview(UUID.randomUUID(), 1, true, true, "A", "A", null))));
        assertThat(screens.load(new ChatContext(ChatContextType.ATTEMPT_REVIEW, attempt, null, null), user, null)
                        .text())
                .contains("Question 1: correct");
        assertThat(ScreenContextAssembler.words("a b c d", 2)).isEqualTo("a b ...");
    }
}
