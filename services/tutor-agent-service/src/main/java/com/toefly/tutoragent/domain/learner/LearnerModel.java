package com.toefly.tutoragent.domain.learner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Long-term memory of the agent about one learner (docs/06 §6.1): one per user, optimistic version. Statistics are a
 * snapshot recomputed from mistake records and opportunities on every change.
 */
public record LearnerModel(
        UUID userId,
        long version,
        @Nullable Instant updatedAt,
        ExplanationLanguage language,
        @Nullable Double targetBand,
        @Nullable LocalDate examDate,
        @Nullable String timezone,
        Map<String, Double> bands,
        List<MistakeStat> mistakeStats) {

    public static final int TOP = 10;

    public LearnerModel {
        bands = Map.copyOf(bands);
        mistakeStats = List.copyOf(mistakeStats);
    }

    public static LearnerModel empty(UUID userId) {
        return new LearnerModel(userId, 0, null, ExplanationLanguage.EN, null, null, null, Map.of(), List.of());
    }

    public LearnerModel withStats(List<MistakeStat> stats, Instant now) {
        return new LearnerModel(userId, version + 1, now, language, targetBand, examDate, timezone, bands, stats);
    }

    public LearnerModel withBands(Map<String, Double> newBands, Instant now) {
        return new LearnerModel(
                userId, version + 1, now, language, targetBand, examDate, timezone, newBands, mistakeStats);
    }

    public LearnerModel withLanguage(ExplanationLanguage newLanguage, Instant now) {
        return new LearnerModel(
                userId, version + 1, now, newLanguage, targetBand, examDate, timezone, bands, mistakeStats);
    }

    public LearnerModel withGoal(
            @Nullable Double target,
            @Nullable LocalDate date,
            @Nullable String zone,
            ExplanationLanguage newLanguage,
            Instant now) {
        return new LearnerModel(userId, version + 1, now, newLanguage, target, date, zone, bands, mistakeStats);
    }

    /** The top subcategories by priority, without resolved ones. */
    public List<MistakeStat> topMistakes(int limit) {
        return mistakeStats.stream()
                .filter(s -> s.status() != MistakeStatus.RESOLVED)
                .limit(limit)
                .toList();
    }
}
