package com.toefly.practice.application.port.out;

import com.toefly.practice.application.port.in.AbilityChange;
import com.toefly.practice.domain.ability.AbilityEstimate;
import com.toefly.practice.domain.ability.Response;
import com.toefly.practice.domain.attempt.Section;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ability_estimate, ability_history and the responses kept until the MAP estimate (docs/03-domain-model.md §6.5). */
public interface AbilityStorePort {

    Optional<AbilityEstimate> find(UUID userId, Section section);

    List<AbilityEstimate> findAll(UUID userId);

    /**
     * Inserts or updates the estimate at its version.
     *
     * @throws ConcurrencyConflictException when the estimate changed meanwhile
     */
    void save(AbilityEstimate estimate);

    /** Records the update of an attempt; false when the attempt was already counted (INV-ABL-02). */
    boolean recordHistory(UUID attemptId, AbilityEstimate before, AbilityEstimate after);

    Optional<AbilityChange> change(UUID attemptId);

    void addResponses(UUID userId, Section section, List<Response> responses);

    List<Response> responses(UUID userId, Section section);

    int eraseUser(UUID userId);
}
