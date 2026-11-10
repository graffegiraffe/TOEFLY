package com.toefly.practice.adapter.in.web;

import com.toefly.practice.adapter.in.web.api.AbilitiesApi;
import com.toefly.practice.adapter.in.web.dto.AbilityList;
import com.toefly.practice.application.port.in.GetAbilityUseCase;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Theta by section (docs/03-domain-model.md §7). */
@RestController
public class AbilityController implements AbilitiesApi {

    private final GetAbilityUseCase abilities;
    private final CurrentCaller caller;

    public AbilityController(GetAbilityUseCase abilities, CurrentCaller caller) {
        this.abilities = abilities;
        this.caller = caller;
    }

    @Override
    @PreAuthorize("hasAnyRole('STUDENT', 'TUTOR')")
    public ResponseEntity<AbilityList> listAbilities(@Nullable UUID studentId) {
        return ResponseEntity.ok(new AbilityList()
                .items(abilities.abilities(caller.get(), studentId).stream()
                        .map(AttemptDtoMapper::ability)
                        .toList()));
    }
}
