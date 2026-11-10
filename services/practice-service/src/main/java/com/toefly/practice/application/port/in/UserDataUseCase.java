package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.time.TimeEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GDPR: export and erasure of everything practice-service keeps about a user (docs/03-domain-model.md §14). */
public interface UserDataUseCase {

    UserData export(UUID userId);

    void erase(UUID userId);

    record UserData(
            UUID userId,
            Instant generatedAt,
            List<AttemptView> attempts,
            List<AbilityEstimate> abilities,
            List<TimeEntry> timeEntries) {}
}
