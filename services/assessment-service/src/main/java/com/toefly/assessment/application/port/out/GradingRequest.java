package com.toefly.assessment.application.port.out;

import com.toefly.assessment.domain.submission.TaskType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One pass of WritingGrader as a call of {@code POST /internal/v1/ai/chat/completions}. */
public record GradingRequest(
        TaskType taskType,
        String passTag,
        UUID userId,
        Map<String, Object> variables,
        List<String> allowList,
        String idempotencyKey,
        @Nullable Integer promptVersion) {

    public GradingRequest {
        variables = Map.copyOf(variables);
        allowList = List.copyOf(allowList);
    }

    public String promptKey() {
        return taskType == TaskType.WRITE_EMAIL ? "writing.grade.email" : "writing.grade.discussion";
    }
}
