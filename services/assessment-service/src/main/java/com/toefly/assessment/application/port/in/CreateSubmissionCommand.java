package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.submission.AttemptMode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code POST /submissions}: the item set and, optionally, the task of the week or exam session it belongs to. */
public record CreateSubmissionCommand(
        UUID itemSetId, @Nullable UUID weekTaskId, @Nullable UUID examSessionId, AttemptMode mode) {}
