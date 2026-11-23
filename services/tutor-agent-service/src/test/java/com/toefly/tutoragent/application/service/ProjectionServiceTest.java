package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.application.port.out.ProjectionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProjectionServiceTest {

    @Test
    void forwardsEveryProjection() {
        List<String> calls = new ArrayList<>();
        ProjectionRepository repository = new ProjectionRepository() {
            @Override
            public void entitlements(UUID userId, Collection<String> entitlements, long version, Instant at) {
                calls.add("entitlements " + entitlements);
            }

            @Override
            public void examStarted(UUID userId, UUID attemptId, Instant at) {
                calls.add("examStarted");
            }

            @Override
            public void examEnded(UUID attemptId) {
                calls.add("examEnded");
            }

            @Override
            public boolean examInProgress(UUID userId, Instant now) {
                return false;
            }

            @Override
            public void activity(UUID userId, Instant at) {
                calls.add("activity");
            }

            @Override
            public void profile(UUID userId, @Nullable String timezone, @Nullable LocalDate examDate) {
                calls.add("profile " + timezone);
            }

            @Override
            public void weekProgress(
                    UUID userId, UUID weekId, int weekNo, int progressPercent, boolean rebalanced, Instant at) {
                calls.add("week " + weekNo + " " + rebalanced);
            }

            @Override
            public void erase(UUID userId) {
                calls.add("erase");
            }
        };
        ProjectionService service = new ProjectionService(repository);
        UUID user = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        service.entitlementsChanged(user, List.of("AI_TUTOR_CHAT"), 3, now);
        service.examAttemptStarted(user, UUID.randomUUID(), now);
        service.examAttemptEnded(UUID.randomUUID());
        service.active(user, now);
        service.profileKnown(user, "Europe/Minsk", null);
        service.weekProgress(user, UUID.randomUUID(), 2, 40, true, now);
        service.accountDeletionRequested(user);
        assertThat(calls)
                .containsExactly(
                        "entitlements [AI_TUTOR_CHAT]",
                        "examStarted",
                        "examEnded",
                        "activity",
                        "profile Europe/Minsk",
                        "week 2 true",
                        "erase");
    }
}
