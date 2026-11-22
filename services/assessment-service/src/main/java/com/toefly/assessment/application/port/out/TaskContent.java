package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.submission.ModelAnswer;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a Writing task shows and what the grader needs from content-service: the prompt text, the texts a response
 * must not copy, fictional names that are not personal data, the bullets of an email and a model answer.
 */
public record TaskContent(
        UUID itemSetId,
        int itemSetVersion,
        TaskType taskType,
        String promptText,
        List<String> sourceTexts,
        List<String> allowList,
        int bulletCount,
        int minWords,
        int timeLimitSeconds,
        @Nullable String modelAnswer,
        @Nullable String modelAnswerSource) {

    public TaskContent {
        sourceTexts = List.copyOf(sourceTexts);
        allowList = List.copyOf(allowList);
    }

    /** The model answer of the task, or null if it has none. */
    public @Nullable ModelAnswer model() {
        return modelAnswer == null || modelAnswer.isBlank() ? null : new ModelAnswer(modelAnswer, modelAnswerSource);
    }
}
