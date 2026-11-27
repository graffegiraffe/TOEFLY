package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Learners that may need a come-back message, and the messages already written. */
public interface ReengagementRepository {

    /** Learners idle since before {@code idleBefore} or whose week was rebalanced after {@code rebalancedAfter}. */
    List<Candidate> candidates(Instant idleBefore, Instant rebalancedAfter, int limit);

    boolean written(UUID userId, Situation situation, String episodeKey);

    void save(
            UUID id,
            UUID userId,
            Situation situation,
            String episodeKey,
            ComeBackMessage message,
            boolean aiFallback,
            Instant at);

    record Candidate(
            UUID userId,
            Instant lastActiveAt,
            @Nullable String timezone,
            @Nullable LocalDate examDate,
            int weekNo,
            int progressPercent,
            @Nullable Instant rebalancedAt) {}
}
