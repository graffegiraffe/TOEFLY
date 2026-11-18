package com.toefly.assessment.application.port.in;

import java.util.Map;

/** A tutor's score of a version: final score 0–5 in steps of 0.5, optional criteria and a note. */
public record TutorEvaluationCommand(int versionNo, double score, Map<String, Integer> criteria, String note) {

    public TutorEvaluationCommand {
        criteria = Map.copyOf(criteria);
    }
}
