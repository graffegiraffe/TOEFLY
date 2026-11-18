package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.out.TaskContent;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Input of WritingGrader for one version (docs/06-ai-agent.md §5.4.2). */
record GradingInput(
        UUID submissionId,
        int versionNo,
        UUID userId,
        TaskContent task,
        String responseText,
        int wordCount,
        int timeSpentSeconds,
        @Nullable Previous previous) {

    /** Score and main issues of the previous version for Attempt 2 and 3, without its text. */
    record Previous(int versionNo, double score, List<String> mainIssues) {}
}
