package com.toefly.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.privacy.AccountDeletionStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsentAndPrivacyServiceTest {

    final Fakes.Profiles profiles = new Fakes.Profiles();
    final Fakes.Consents consents = new Fakes.Consents();
    final Fakes.Requests requests = new Fakes.Requests();
    final Fakes.Events events = new Fakes.Events();
    final Fakes.Identity identity = new Fakes.Identity();
    final ProfileCommandService profileService =
            new ProfileCommandService(profiles, consents, events, identity, Fakes.CLOCK, "2026-11-01");
    final ConsentCommandService consentService =
            new ConsentCommandService(profileService, consents, events, Fakes.CLOCK, "2026-11-01");
    final PrivacyCommandService privacyService =
            new PrivacyCommandService(profileService, requests, events, identity, identity, Fakes.CLOCK);

    final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("AC-31.01.2 every change is journaled and the latest answer is current")
    void consentJournal() {
        consentService.recordConsent(Fakes.student(userId), ConsentType.AI_PROCESSING, true, ConsentSource.WEB);
        consentService.recordConsent(Fakes.student(userId), ConsentType.MARKETING_EMAILS, false, ConsentSource.WEB);

        assertThat(consents.journal).hasSize(2);
        assertThat(consentService.currentConsents(Fakes.student(userId)))
                .extracting(c -> c.type())
                .containsExactly(ConsentType.AI_PROCESSING, ConsentType.MARKETING_EMAILS);
        assertThat(events.published).contains("consent-changed:AI_PROCESSING", "consent-changed:MARKETING_EMAILS");
    }

    @Test
    @DisplayName("AC-31.03.1 an export request is recorded and found by its owner only")
    void exportRequest() {
        var export = privacyService.requestExport(Fakes.student(userId));

        assertThat(privacyService.findExport(Fakes.student(userId), export.id()))
                .contains(export);
        assertThat(privacyService.findExport(Fakes.student(UUID.randomUUID()), export.id()))
                .isEmpty();
    }

    @Test
    @DisplayName("AC-31.04.1 deletion publishes the event, disables the account and revokes sessions")
    void deletion() {
        var deletion = privacyService.requestDeletion(Fakes.student(userId), "DELETE");

        assertThat(deletion.status()).isEqualTo(AccountDeletionStatus.REQUESTED);
        assertThat(events.published).contains("account-deletion-requested");
        assertThat(identity.calls).containsExactly("disableUser", "revokeSessions");
        assertThat(privacyService.currentDeletion(Fakes.student(userId))).contains(deletion);
    }

    @Test
    void deletionSurvivesGatewayOutage() {
        identity.failRevocation = true;

        privacyService.requestDeletion(Fakes.student(userId), "DELETE");

        assertThat(identity.calls).containsExactly("disableUser");
        assertThat(requests.deletions).hasSize(1);
    }
}
