package com.toefly.assessment.evals;

import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.service.GradingInput;
import com.toefly.assessment.application.service.GradingResult;
import com.toefly.assessment.application.service.WritingGrader;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.PassRecord;
import com.toefly.assessment.domain.grading.QuoteLocator;
import com.toefly.assessment.domain.submission.ResponseText;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs the real WritingGrader over a suite, one case at a time to stay inside free quotas. */
public final class EvalRunner {

    static final UUID EVAL_USER = UUID.fromString("00000000-0000-4000-8000-0000000e0a15");

    private final WritingGrader grader;
    final List<String> errors = new ArrayList<>();

    public EvalRunner(WritingGrader grader) {
        this.grader = grader;
    }

    public List<CaseResult> run(List<EvalCase> cases) {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            try {
                results.add(run(c));
            } catch (RuntimeException failure) {
                errors.add(c.id() + ": " + failure.getMessage());
                System.err.println("eval case " + c.id() + " failed: " + failure.getMessage());
            }
        }
        return results;
    }

    CaseResult run(EvalCase c) {
        TaskType type = TaskType.valueOf(c.taskType());
        String prompt = c.taskPrompt() == null ? "Topic: " + c.topic() : c.taskPrompt();
        TaskContent task = new TaskContent(
                UUID.nameUUIDFromBytes(c.topic().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                1,
                type,
                prompt,
                List.of(prompt),
                List.of(),
                type == TaskType.WRITE_EMAIL ? 3 : 0,
                type == TaskType.WRITE_EMAIL ? 0 : 100,
                type.timeLimitSeconds(),
                null,
                null);
        String text = ResponseText.normalize(c.text());
        GradingResult result = grader.grade(new GradingInput(
                UUID.nameUUIDFromBytes(c.id().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                1,
                EVAL_USER,
                task,
                text,
                ResponseText.wordCount(text),
                type.timeLimitSeconds(),
                null));
        int found = 0;
        for (EvalCase.TutorError error : c.tutorErrors()) {
            var span = QuoteLocator.locate(text, error.quote(), 1);
            if (span.isPresent() && result.annotations().stream().anyMatch(a -> overlapsHalf(a, span.get()))) {
                found++;
            }
        }
        return new CaseResult(
                c.id(),
                c.split(),
                c.gold(),
                result.finalScore(),
                result.passes().stream().map(PassRecord::score).toList(),
                result.integrityFlags(),
                c.distortionOf(),
                c.tutorErrors().size(),
                found,
                result.annotations().size(),
                (int) result.annotations().stream().filter(InlineAnnotation::anchored).count());
    }

    private static boolean overlapsHalf(InlineAnnotation a, QuoteLocator.Range tutor) {
        if (!a.anchored()) {
            return false;
        }
        int overlap = Math.min(a.end(), tutor.end()) - Math.max(a.start(), tutor.start());
        return overlap > 0 && overlap * 2 >= tutor.end() - tutor.start();
    }
}
