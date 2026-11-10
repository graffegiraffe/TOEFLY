package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import java.util.UUID;

/** An approved item set the planner may give the learner. {@code repeat} marks a set the learner did before. */
public record ItemSetCandidate(
        UUID itemSetId, TaskType taskType, String title, double estimatedMinutes, double difficultyB, boolean repeat) {}
