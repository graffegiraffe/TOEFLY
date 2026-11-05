package com.toefly.profile.application.service;

import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.RequestAccountDeletionUseCase;
import com.toefly.profile.application.port.in.RequestDataExportUseCase;
import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.application.port.out.IdentityProviderPort;
import com.toefly.profile.application.port.out.PrivacyRequestStorePort;
import com.toefly.profile.application.port.out.ProfileEventPublisherPort;
import com.toefly.profile.application.port.out.SessionRevocationPort;
import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.privacy.DataExport;
import com.toefly.profile.domain.profile.UserProfile;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR requests. The export is recorded here and collected once the services implement their export endpoints;
 * deletion is coordinated through account-deletion-requested and every service deletes its own data.
 */
@Service
public class PrivacyCommandService implements RequestDataExportUseCase, RequestAccountDeletionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PrivacyCommandService.class);

    private final GetProfileUseCase profiles;
    private final PrivacyRequestStorePort requests;
    private final ProfileEventPublisherPort events;
    private final IdentityProviderPort identity;
    private final SessionRevocationPort sessions;
    private final Clock clock;

    public PrivacyCommandService(
            GetProfileUseCase profiles,
            PrivacyRequestStorePort requests,
            ProfileEventPublisherPort events,
            IdentityProviderPort identity,
            SessionRevocationPort sessions,
            Clock clock) {
        this.profiles = profiles;
        this.requests = requests;
        this.events = events;
        this.identity = identity;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DataExport requestExport(SignedInUser user) {
        profiles.getOrCreate(user);
        DataExport export = DataExport.request(
                user.userId(), requests.latestExport(user.userId()).orElse(null), clock.instant());
        requests.saveExport(export);
        return export;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DataExport> findExport(SignedInUser user, UUID exportId) {
        return requests.findExport(user.userId(), exportId);
    }

    @Override
    @Transactional
    public AccountDeletion requestDeletion(SignedInUser user, String confirmation) {
        UserProfile profile = profiles.getOrCreate(user);
        AccountDeletion deletion = AccountDeletion.request(
                user.userId(),
                confirmation,
                user.authTime(),
                requests.latestDeletion(user.userId()).orElse(null),
                clock.instant());
        requests.saveDeletion(deletion);
        events.accountDeletionRequested(deletion, profile.roles());
        identity.disableUser(user.userId());
        try {
            sessions.revokeSessions(user.userId());
        } catch (RuntimeException e) {
            // Keycloak already ended the sessions; access tokens expire within 15 minutes.
            log.warn("Could not revoke web sessions of a deleted account", e);
        }
        return deletion;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDeletion> currentDeletion(SignedInUser user) {
        return requests.latestDeletion(user.userId());
    }
}
