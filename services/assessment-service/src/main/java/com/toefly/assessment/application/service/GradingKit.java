package com.toefly.assessment.application.service;

import com.toefly.assessment.domain.grading.Taxonomy;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Static material of WritingGrader: the taxonomy of mistakes and the few-shot anchors per task type (own synthetic
 * responses, docs/12 phase 5b decision 4). Every pass sees the whole ladder of its task type, highest score first:
 * with anchors split between passes, pass A had no 4 and scored good answers as 3 (phase 5b decision 16).
 */
public record GradingKit(Taxonomy taxonomy, Map<TaskType, List<Anchor>> anchors) {

    public record Anchor(String pass, int score, String task, String text, String reason) {}

    public GradingKit {
        anchors = Map.copyOf(anchors);
    }

    String anchorsFor(TaskType type) {
        StringBuilder out = new StringBuilder();
        List<Anchor> ladder = anchors.getOrDefault(type, List.of()).stream()
                .sorted(Comparator.comparingInt(Anchor::score).reversed())
                .toList();
        for (Anchor anchor : ladder) {
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
        return out.toString().strip();
    }
}
