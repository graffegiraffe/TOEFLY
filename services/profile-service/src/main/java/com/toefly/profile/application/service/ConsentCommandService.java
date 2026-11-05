package com.toefly.profile.application.service;

import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.ManageConsentsUseCase;
import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.application.port.out.ConsentStorePort;
import com.toefly.profile.application.port.out.ProfileEventPublisherPort;
import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentCommandService implements ManageConsentsUseCase {

    private final GetProfileUseCase profiles;
    private final ConsentStorePort consents;
    private final ProfileEventPublisherPort events;
    private final Clock clock;
    private final String policyVersion;

    public ConsentCommandService(
            GetProfileUseCase profiles,
            ConsentStorePort consents,
            ProfileEventPublisherPort events,
            Clock clock,
            @Value("${toefly.profile.policy-version}") String policyVersion) {
        this.profiles = profiles;
        this.consents = consents;
        this.events = events;
        this.clock = clock;
        this.policyVersion = policyVersion;
    }

    @Override
    @Transactional
    public List<Consent> currentConsents(SignedInUser user) {
        profiles.getOrCreate(user);
        return consents.latestPerType(user.userId());
    }

    @Override
    @Transactional
    public Consent recordConsent(SignedInUser user, ConsentType type, boolean granted, ConsentSource source) {
        profiles.getOrCreate(user);
        Consent consent =
                Consent.record(user.userId(), type, granted, policyVersion, source, user.clientIp(), clock.instant());
        consents.append(consent);
        events.consentChanged(consent);
        return consent;
    }
}
