package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.port.out.AiUnavailableException;
import com.toefly.assessment.application.port.out.GradingRequest;
import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.PassRecord;
import com.toefly.assessment.domain.grading.CappedScore;
import com.toefly.assessment.domain.grading.CommentMerger;
import com.toefly.assessment.domain.grading.Consensus;
import com.toefly.assessment.domain.grading.PassOutput;
import com.toefly.assessment.domain.grading.QuoteLocator;
import com.toefly.assessment.domain.grading.RawComment;
import com.toefly.assessment.domain.grading.TextFacts;
import com.toefly.assessment.domain.submission.TaskType;
import io.micrometer.observation.annotation.Observed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Worker {@code WritingGrader} (docs/06-ai-agent.md §5.4): deterministic checks, passes A and B in parallel on two
 * models, pass C when they differ by more than one point, caps, and merged comments anchored to the text.
 */
@Component
public class WritingGrader {

    static final double MIN_ANCHORED = 0.5;

    private final AiGradingPort ai;
    private final GradingKit kit;

    public WritingGrader(AiGradingPort ai, GradingKit kit) {
        this.ai = ai;
        this.kit = kit;
    }

    @Observed(name = "toefly.assessment.writing.grading")
    public GradingResult grade(GradingInput input) {
        TaskType type = input.task().taskType();
        String text = input.responseText();
        TextFacts facts = TextFacts.of(text, input.task().sourceTexts());
        if (facts.wordCount() == 0 || facts.copiedShare() >= 0.70) {
            return withoutModel(type, facts);
        }
        PassOutput a;
        PassOutput b;
        try (ExecutorService passes = io.micrometer.context.ContextExecutorService.wrap(
                Executors.newVirtualThreadPerTaskExecutor(),
                () -> io.micrometer.context.ContextSnapshotFactory.builder()
                        .build()
                        .captureAll())) {
            Future<PassOutput> futureA = passes.submit(() -> pass(input, "A"));
            Future<PassOutput> futureB = passes.submit(() -> pass(input, "B"));
            a = await(futureA);
            b = await(futureB);
        }
        PassOutput c = Consensus.needsThirdPass(a.score(), b.score()) ? pass(input, "C") : null;
        Consensus consensus = Consensus.of(a.score(), b.score(), c == null ? null : c.score());
        List<PassOutput> all = c == null ? List.of(a, b) : List.of(a, b, c);
        PassOutput primary = all.stream()
                .min(Comparator.comparingDouble(p -> Math.abs(p.score() - consensus.finalScore())))
                .orElseThrow();
        PassOutput secondary =
                all.stream().filter(p -> p != primary).findFirst().orElseThrow();
        CappedScore capped = CappedScore.apply(type, consensus.finalScore(), facts, primary.bulletCoverage());
        Set<String> flags = new LinkedHashSet<>();
        all.forEach(p -> flags.addAll(p.integrityFlags()));
        if (facts.instructionSuspected()) {
            flags.add("INSTRUCTION_IN_RESPONSE");
        }
        if (capped.capsApplied().contains(CappedScore.NOT_ENGLISH)) {
            flags.add("NOT_ENGLISH");
        }
        return new GradingResult(
                capped.score(),
                consensus.agreement(),
                consensus.needsHumanReview(),
                primary.criteria(),
                primary.strengths(),
                primary.priorities(),
                CommentMerger.merge(text, primary.comments(), secondary.comments(), kit.taxonomy()),
                all.stream()
                        .map(p -> new PassRecord(p.passTag(), p.score(), p.provider(), p.model(), p.degraded()))
                        .toList(),
                capped.capsApplied(),
                List.copyOf(flags),
                primary.promptKey(),
                primary.promptVersion(),
                primary.model(),
                all.stream().anyMatch(PassOutput::degraded));
    }

    /** One pass; repeated once with the missing quotes when fewer than half of them are found (§5.4.5). */
    private PassOutput pass(GradingInput input, String passTag) {
        PassOutput output = ai.grade(request(input, passTag, ""));
        double anchored = CommentMerger.anchoredShare(input.responseText(), output.comments());
        if (anchored >= MIN_ANCHORED) {
            return output;
        }
        String missing = output.comments().stream()
                .map(RawComment::quote)
                .filter(q -> QuoteLocator.locate(input.responseText(), q, 1).isEmpty())
                .limit(10)
                .collect(Collectors.joining("\", \"", "quotes not found: \"", "\""));
        return ai.grade(request(input, passTag, missing));
    }

    GradingRequest request(GradingInput input, String passTag, String previousViolations) {
        TaskType type = input.task().taskType();
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("explanationLanguage", "English");
        variables.put("taskText", input.task().promptText());
        variables.put("anchors", kit.anchorsFor(type));
        variables.put("previousVersionBlock", previousBlock(input.previous()));
        variables.put("responseText", input.responseText());
        variables.put("wordCount", input.wordCount());
        variables.put("timeSpentSec", input.timeSpentSeconds());
        variables.put("timeLimitSec", input.task().timeLimitSeconds());
        variables.put("previousViolations", previousViolations);
        String key = "WritingGrader:" + input.submissionId() + ":" + input.versionNo() + ":" + passTag
                + (previousViolations.isEmpty() ? "" : ":retry");
        return new GradingRequest(
                type, passTag, input.userId(), variables, input.task().allowList(), key, null);
    }

    private static String previousBlock(GradingInput.@Nullable Previous previous) {
        if (previous == null) {
            return "";
        }
        String issues = previous.mainIssues().isEmpty() ? "none recorded" : String.join(", ", previous.mainIssues());
        return "PREVIOUS VERSION\nVersion " + previous.versionNo() + " scored " + previous.score() + "; main issues: "
                + issues + ". Check whether they were fixed.";
    }

    private static GradingResult withoutModel(TaskType type, TextFacts facts) {
        CappedScore capped = CappedScore.apply(type, 0, facts, List.of());
        String reason = capped.capsApplied().contains(CappedScore.COPIED)
                ? "The response repeats the task instead of answering it."
                : "There is no response to score.";
        List<CriterionScore> criteria = new ArrayList<>();
        for (String criterion : CriterionScore.WRITING) {
            criteria.add(new CriterionScore(criterion, 0, reason));
        }
        return new GradingResult(
                0,
                null,
                false,
                criteria,
                List.of(),
                List.of("Write your own answer to the task in full sentences."),
                List.of(),
                List.of(),
                capped.capsApplied(),
                capped.capsApplied().contains(CappedScore.COPIED) ? List.of("COPIED_FROM_PROMPT") : List.of(),
                null,
                0,
                null,
                false);
    }

    private static PassOutput await(Future<PassOutput> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("grading interrupted", null, false, interrupted);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AiUnavailableException("grading failed", null, false, failed);
        }
    }
}
