package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** assessment-service internal API: submissions with evaluations, and the band estimate. */
public interface AssessmentPort {

    Submission submission(UUID submissionId, @Nullable Integer version, @Nullable String bearer);

    /** Band estimate by section and OVERALL; empty when there is not enough data. */
    Map<String, Double> bandEstimate(UUID userId, @Nullable String bearer);

    record Submission(
            UUID submissionId,
            UUID userId,
            String taskType,
            String status,
            List<Version> versions,
            @Nullable String modelAnswer) {
        public Submission {
            versions = List.copyOf(versions);
        }

        public boolean evaluated() {
            return status.equals("EVALUATED") || status.equals("TUTOR_REVIEWED");
        }
    }

    record Version(
            int versionNo,
            String status,
            String text,
            int wordCount,
            @Nullable Double finalScore,
            List<Comment> comments,
            @Nullable Instant evaluatedAt) {
        public Version {
            comments = List.copyOf(comments);
        }
    }

    record Comment(
            String quote,
            String comment,
            @Nullable String suggestion,
            String category,
            String subcategory,
            String severity) {}
}
