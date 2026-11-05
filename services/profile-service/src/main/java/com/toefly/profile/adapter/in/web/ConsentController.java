package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.api.ConsentsApi;
import com.toefly.profile.adapter.in.web.dto.Consent;
import com.toefly.profile.adapter.in.web.dto.ConsentList;
import com.toefly.profile.adapter.in.web.dto.ConsentRequest;
import com.toefly.profile.adapter.in.web.dto.ConsentType;
import com.toefly.profile.application.port.in.ManageConsentsUseCase;
import com.toefly.profile.domain.consent.ConsentSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsentController implements ConsentsApi {

    private final ManageConsentsUseCase consents;
    private final CurrentUser currentUser;

    public ConsentController(ManageConsentsUseCase consents, CurrentUser currentUser) {
        this.consents = consents;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ConsentList> listConsents() {
        return ResponseEntity.ok(new ConsentList()
                .items(consents.currentConsents(currentUser.get()).stream()
                        .map(ProfileDtos::consent)
                        .toList()));
    }

    @Override
    public ResponseEntity<Consent> recordConsent(ConsentType consentType, ConsentRequest request) {
        var source = request.getSource() == ConsentRequest.SourceEnum.IOS ? ConsentSource.IOS : ConsentSource.WEB;
        var consent = consents.recordConsent(
                currentUser.get(),
                com.toefly.profile.domain.consent.ConsentType.valueOf(consentType.getValue()),
                request.getGranted(),
                source);
        return ResponseEntity.ok(ProfileDtos.consent(consent));
    }
}
