package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.api.AccountApi;
import com.toefly.profile.adapter.in.web.dto.BecomeTutorRequest;
import com.toefly.profile.adapter.in.web.dto.UserProfile;
import com.toefly.profile.application.port.in.BecomeTutorUseCase;
import com.toefly.profile.application.port.in.RevokeSessionsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController implements AccountApi {

    private final RevokeSessionsUseCase revokeSessions;
    private final BecomeTutorUseCase becomeTutor;
    private final CurrentUser currentUser;

    public AccountController(
            RevokeSessionsUseCase revokeSessions, BecomeTutorUseCase becomeTutor, CurrentUser currentUser) {
        this.revokeSessions = revokeSessions;
        this.becomeTutor = becomeTutor;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<Void> signOutEverywhere() {
        revokeSessions.signOutEverywhere(currentUser.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserProfile> becomeTutor(String idempotencyKey, BecomeTutorRequest request) {
        var profile = becomeTutor.becomeTutor(currentUser.get(), Boolean.TRUE.equals(request.getAcceptTutorTerms()));
        return ResponseEntity.ok().eTag(ETags.of(profile.version())).body(ProfileDtos.profile(profile));
    }
}
