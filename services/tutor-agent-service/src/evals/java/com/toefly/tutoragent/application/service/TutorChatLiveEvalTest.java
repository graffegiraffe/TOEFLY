package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.application.port.in.ChatUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.ChatModelPort;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.domain.chat.AnswerLeakDetector;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeCategory;
import com.toefly.tutoragent.domain.learner.MistakeStat;
import com.toefly.tutoragent.domain.learner.MistakeStatus;
import com.toefly.tutoragent.domain.learner.MistakeTrend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * Live evals of the chat (`tutor-chat`, docs/12 phase 5c decision 3): the real orchestrator, router and chat model
 * through ai-gateway over synthetic learners, labelled by the judge. Run with {@code -Pevals -Devals.live=true} and a
 * running ai-gateway and Keycloak; the report goes to src/evals/reports/tutor-chat.json.
 */
@EnabledIfSystemProperty(named = "evals.live", matches = "true")
class TutorChatLiveEvalTest {

    static final List<String> PROMPTS = List.of("tutor.chat.system", "tutor.router", "tutor.eval.judge");

    @Test
    void runTutorChat() throws Exception {
        TutorEvals live = new TutorEvals();
        AiPort ai = live.ai();
        ChatModelPort model = live.chatModel();
        JsonNode suite = TutorEvals.read(Path.of("src/evals/resources/tutor-chat.json"));
        String only = System.getProperty("evals.cases", "");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode c : suite.path("cases")) {
            if (!only.isBlank() && !List.of(only.split(",")).contains(c.path("id").asString())) {
                continue;
            }
            results.add(run(c, live, ai, model));
            System.out.println("case " + c.path("id").asString() + ": " + results.getLast().get("pass"));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "tutor-chat");
        report.put("runAt", Instant.now().toString());
        report.put("goldSource", "SCENARIO_LABELS");
        report.put("promptSha256", TutorEvals.promptHashes(PROMPTS));
        boolean merge = !only.isBlank() && Boolean.getBoolean("evals.merge");
        if (merge) {
            results = mergeInto(TutorEvals.REPORTS.resolve("tutor-chat.json"), results, report);
        }
        List<JsonNode> nodes = results.stream().map(r -> TutorEvals.JSON.valueToTree(r)).map(JsonNode.class::cast).toList();
        Map<String, Double> metrics = TutorEvalMetrics.chat(nodes);
        report.put("metrics", metrics);
        report.put("misses", TutorEvalMetrics.misses(metrics, TutorEvalMetrics.CHAT_THRESHOLDS));
        report.put("cases", results);
        Files.createDirectories(TutorEvals.REPORTS);
        Files.writeString(
                TutorEvals.REPORTS.resolve(only.isBlank() || merge ? "tutor-chat.json" : "tutor-chat-partial.json"),
                TutorEvals.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        System.out.println("tutor-chat metrics " + metrics + " misses " + report.get("misses"));
        assertThat(results).isNotEmpty();
    }

    /**
     * Replaces the rerun cases in the last full report, keeps its run time and adds the rerun to {@code reruns}; the
     * prompts must be the ones the full report was made with.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mergeInto(Path file, List<Map<String, Object>> rerun, Map<String, Object> report) {
        Map<String, Object> full = TutorEvals.JSON.convertValue(TutorEvals.read(file), Map.class);
        assertThat(full.get("promptSha256")).as("prompts changed since the full run").isEqualTo(report.get("promptSha256"));
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        rerun.forEach(r -> byId.put((String) r.get("id"), r));
        List<Map<String, Object>> cases = ((List<Map<String, Object>>) full.get("cases")).stream()
                .map(c -> byId.getOrDefault((String) c.get("id"), c))
                .toList();
        List<Object> reruns = new ArrayList<>((List<Object>) full.getOrDefault("reruns", List.of()));
        reruns.add(Map.of("runAt", report.get("runAt"), "cases", List.copyOf(byId.keySet())));
        report.put("runAt", full.get("runAt"));
        report.put("reruns", reruns);
        return cases;
    }

    Map<String, Object> run(JsonNode c, TutorEvals live, AiPort ai, ChatModelPort liveModel) {
        UUID user = TutorEvals.id(c.path("id").asString());
        Fakes.Models models = new Fakes.Models();
        Fakes.Mistakes mistakes = new Fakes.Mistakes();
        Fakes.Memory memory = new Fakes.Memory();
        Fakes.Practice practice = new Fakes.Practice();
        Fakes.Results results = new Fakes.Results();
        Fakes.Assessment assessment = new Fakes.Assessment();
        Fakes.Reviews reviews = new Fakes.Reviews();
        ExplanationLanguage language = ExplanationLanguage.of(c.path("language").asString("EN"));
        LearnerModel seeded = learner(user, c.path("learner"), language);
        models.save(seeded);
        assessment.bands = seeded.bands();
        EvalOwners owners = new EvalOwners(c.path("owners"));
        ChatContext context = screen(c.path("screen"), user, practice, results, assessment);
        StringBuilder facts = new StringBuilder(c.path("owners").toString()).append(c.path("learner")).append(c.path("screen"));
        ChatModelPort recording = new ChatModelPort() {
            @Override
            public Turn stream(TurnRequest request, Consumer<String> onToken) {
                request.messages().forEach(m -> facts.append(m.content()));
                return liveModel.stream(request, onToken);
            }
        };
        LearnerModelService learner = new LearnerModelService(
                models, mistakes, memory, new Fakes.Publications(), new Fakes.LearnerEvents(), results,
                practice, assessment, Fakes.taxonomy(), Fakes.TX, java.time.Clock.systemUTC());
        ToolExecutor tools = new ToolExecutor(
                owners, results, practice, assessment, learner, mistakes, memory, reviews, ai, Fakes.taxonomy(), java.time.Clock.systemUTC());
        ChatService chat = new ChatService(
                new ChatScenarioTest.Chats(),
                new ChatScenarioTest.Actions(),
                (t, m, tool, args, status, ms) -> {},
                new Fakes.Rights(),
                new ChatScenarioTest.Exams(),
                learner,
                ai,
                recording,
                live.prompts(),
                owners,
                tools,
                new ScreenContextAssembler(practice, results, assessment, owners, reviews),
                com.toefly.tutoragent.config.EvalToolSpecs.load(),
                Fakes.taxonomy(),
                Fakes.TX,
                java.time.Clock.systemUTC());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.path("id").asString());
        result.put("category", c.path("category").asString());
        try {
            var thread = chat.createThread(user, null, context);
            if (language == ExplanationLanguage.RU) {
                chat.updateThread(user, thread.id(), null, ExplanationLanguage.RU);
            }
            List<String[]> events = new ArrayList<>();
            ChatUseCase.Turn turn = chat.accept(user, null, thread.id(), c.path("message").asString(), ChatUseCase.ContextChoice.thread());
            chat.answer(turn, (seq, name, data) -> events.add(new String[] {name, TutorEvals.JSON.writeValueAsString(data)}));
            String answer = events.stream()
                    .filter(e -> e[0].equals("token"))
                    .map(e -> TutorEvals.JSON.readTree(e[1]).path("t").asString())
                    .reduce("", String::concat);
            String intent = events.stream().filter(e -> e[0].equals("meta"))
                    .map(e -> TutorEvals.JSON.readTree(e[1]).path("intent").asString()).findFirst().orElse("");
            String contextType = events.stream().filter(e -> e[0].equals("meta"))
                    .map(e -> TutorEvals.JSON.readTree(e[1]).path("contextType").asString()).findFirst().orElse("GENERAL");
            List<String> toolNames = events.stream().filter(e -> e[0].equals("tool") && e[1].contains("started"))
                    .map(e -> TutorEvals.JSON.readTree(e[1]).path("name").asString()).toList();
            boolean error = events.stream().anyMatch(e -> e[0].equals("error"));
            boolean leak = AnswerLeakDetector.leaks(
                    answer, ChatContextType.valueOf(contextType), List.of(), c.path("screen").path("draft").asString(""));
            boolean grounded = TutorEvals.numbersGrounded(answer, facts.toString());
            String situation = "Category: " + c.path("category").asString() + ". Screen: " + c.path("screen") + ". Student data: "
                    + c.path("learner") + " " + c.path("owners") + ". Student message: " + c.path("message").asString();
            Map<String, Object> labels = answer.isBlank() ? Map.of() : live.judge(ai, situation, answer);
            result.put("intent", intent);
            result.put("contextType", contextType);
            result.put("tools", toolNames);
            result.put("answer", answer);
            result.put("leakDetected", leak);
            result.put("numbersGrounded", grounded);
            result.put("labels", labels);
            if (error) {
                result.put("error", "the chat ended with an error event");
            }
            result.put("pass", !error && pass(c.path("category").asString(), intent, labels, leak, grounded));
        } catch (RuntimeException failure) {
            result.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            result.put("pass", false);
        }
        return result;
    }

    static boolean pass(String category, String intent, Map<String, Object> labels, boolean leak, boolean grounded) {
        return switch (category) {
            case "SOCRATIC" -> Boolean.TRUE.equals(labels.get("socratic"));
            case "GROUNDING" -> grounded && Boolean.TRUE.equals(labels.get("usesStudentData"));
            case "OFF_TOPIC" -> intent.equals("OFF_TOPIC") || Boolean.TRUE.equals(labels.get("refusesOffTopic"));
            case "NO_LEAK" -> !leak && !Boolean.TRUE.equals(labels.get("givesAnswer"));
            default -> false;
        };
    }

    static LearnerModel learner(UUID user, JsonNode learner, ExplanationLanguage language) {
        List<MistakeStat> stats = new ArrayList<>();
        for (JsonNode m : learner.path("mistakes")) {
            String code = m.path("subcategory").asString();
            int count = m.path("count").asInt();
            stats.add(new MistakeStat(code, MistakeCategory.ofSubcategory(code), count, 0.4, MistakeTrend.STABLE,
                    MistakeStatus.ACTIVE, count, count, count, count * 2, Instant.now(), Fakes.taxonomy().unitRefs(code)));
        }
        Map<String, Double> bands = TutorEvals.JSON.convertValue(learner.path("bands"), new TypeReference<Map<String, Double>>() {});
        return new LearnerModel(user, 1, Instant.now(), language, 4.5, null, "Europe/Minsk", bands == null ? Map.of() : bands, stats);
    }

    static @Nullable ChatContext screen(
            JsonNode screen, UUID user, Fakes.Practice practice, Fakes.Results results, Fakes.Assessment assessment) {
        String type = screen.path("type").asString("GENERAL");
        switch (type) {
            case "ATTEMPT_REVIEW" -> {
                UUID attempt = UUID.randomUUID();
                List<PracticePort.ItemReview> items = new ArrayList<>();
                for (JsonNode i : screen.path("items")) {
                    items.add(new PracticePort.ItemReview(UUID.randomUUID(), i.path("ordinal").asInt(), i.path("correct").asBoolean(),
                            true, i.path("answer").asString(), i.path("key").asString(), i.path("explanation").asString()));
                }
                results.own(attempt, user);
                practice.reviews.put(attempt, new PracticePort.AttemptReview(attempt, screen.path("taskType").asString(), items));
                return new ChatContext(ChatContextType.ATTEMPT_REVIEW, attempt, items.getFirst().itemId(), null);
            }
            case "ATTEMPT_IN_PROGRESS" -> {
                return new ChatContext(ChatContextType.ATTEMPT_REVIEW, UUID.randomUUID(), null, null);
            }
            case "SUBMISSION_DRAFT" -> {
                UUID id = UUID.randomUUID();
                assessment.submissions.put(id, new AssessmentPort.Submission(id, user, screen.path("taskType").asString(), "DRAFT",
                        List.of(new AssessmentPort.Version(1, "DRAFT", screen.path("draft").asString(""), 10, null, List.of(), null)), null));
                return new ChatContext(ChatContextType.SUBMISSION_REVIEW, id, null, null);
            }
            default -> {
                return null;
            }
        }
    }

    /** Owner answers of one scenario. */
    static final class EvalOwners implements OwnerToolsPort {
        private final JsonNode data;

        EvalOwners(JsonNode data) {
            this.data = data;
        }

        private Map<String, Object> map(String field) {
            Map<String, Object> value = TutorEvals.JSON.convertValue(data.path(field), new TypeReference<Map<String, Object>>() {});
            return value == null ? Map.of() : value;
        }

        @Override
        public Map<String, Object> currentWeek(UUID userId, @Nullable String bearer) {
            Map<String, Object> week = new LinkedHashMap<>(map("week"));
            week.putIfAbsent("weekId", UUID.nameUUIDFromBytes(userId.toString().getBytes()).toString());
            return week;
        }

        @Override
        public Map<String, Object> addExtraTask(UUID weekId, Map<String, Object> request, String key) {
            return Map.of("weekTaskId", UUID.randomUUID().toString());
        }

        @Override
        public Map<String, Object> dashboard(UUID userId, @Nullable String bearer) {
            return map("dashboard");
        }

        @Override
        public Map<String, Object> timeReport(UUID userId, String period, @Nullable String bearer) {
            return map("dashboard");
        }

        @Override
        public Map<String, Object> searchItemSets(Map<String, Object> query) {
            return Map.of("items", List.of(Map.of(
                    "itemSetId", "3f2a1b9c-0000-4000-8000-000000000001", "taskType", query.getOrDefault("taskType", "READ_ACADEMIC_PASSAGE"),
                    "title", "Practice set", "estimatedMinutes", 12)));
        }

        @Override
        public Map<String, Object> searchGrammarUnits(@Nullable String topic, @Nullable String subcategory, int limit) {
            return Map.of("units", List.of(Map.of("code", "ROUNDUP5.U10", "title", "Round-Up Unit 10: Nouns – Articles")));
        }
    }
}
