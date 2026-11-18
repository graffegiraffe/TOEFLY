package com.toefly.assessment.application.service;

import com.toefly.assessment.domain.grading.Taxonomy;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.List;
import java.util.Map;

/**
 * Static material of WritingGrader: the taxonomy of mistakes and the few-shot anchors per task type (own synthetic
 * responses, docs/12 phase 5b decision 4). Pass A sees anchors 5 and 3, pass B anchors 4 and 2, pass C all four.
 */
public record GradingKit(Taxonomy taxonomy, Map<TaskType, List<Anchor>> anchors) {

    public record Anchor(String pass, int score, String task, String text, String reason) {}

    public GradingKit {
        anchors = Map.copyOf(anchors);
    }

    String anchorsFor(TaskType type, String passTag) {
        StringBuilder out = new StringBuilder();
        for (Anchor anchor : anchors.getOrDefault(type, List.of())) {
            if (passTag.equals("C") || anchor.pass().equals(passTag)) {
                out.append("Task: ")
                        .append(anchor.task())
                        .append('\n')
                        .append("Response (score ")
                        .append(anchor.score())
                        .append("):\n")
                        .append(anchor.text())
                        .append('\n')
                        .append("Why: ")
                        .append(anchor.reason())
                        .append("\n\n");
            }
        }
        return out.toString().strip();
    }
}
