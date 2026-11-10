package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.ability.AbilityEstimate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface GetAbilityUseCase {

    List<AbilityEstimate> abilities(Caller caller, @Nullable UUID studentId);

    List<AbilityEstimate> abilitiesOf(UUID userId);
}
