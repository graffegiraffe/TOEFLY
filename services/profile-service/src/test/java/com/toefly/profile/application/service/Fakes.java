package com.toefly.profile.application.service;

import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.application.port.out.ConsentStorePort;
import com.toefly.profile.application.port.out.IdentityProviderPort;
import com.toefly.profile.application.port.out.PrivacyRequestStorePort;
import com.toefly.profile.application.port.out.ProfileEventPublisherPort;
import com.toefly.profile.application.port.out.ProfileStorePort;
import com.toefly.profile.application.port.out.SessionRevocationPort;
import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.privacy.DataExport;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** In-memory ports of application tests. */
final class Fakes {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);

    private Fakes() {}

    static SignedInUser student(UUID id) {
        return new SignedInUser(
                id, "kate@example.com", true, "Kate", Set.of(Role.STUDENT), null, Set.of(), CLOCK.instant(), null);
    }

    static class Profiles implements ProfileStorePort {
        final Map<UUID, UserProfile> rows = new HashMap<>();
        final List<UserProfile> goalRevisions = new ArrayList<>();

        @Override
        public Optional<UserProfile> findById(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public boolean insertIfAbsent(UserProfile profile) {
            return rows.putIfAbsent(profile.userId(), profile) == null;
        }

        @Override
        public void update(UserProfile profile, long previousVersion) {
            if (rows.get(profile.userId()).version() != previousVersion) {
                throw new IllegalStateException("lost update");
            }
            rows.put(profile.userId(), profile);
        }

        @Override
        public void appendGoalRevision(UserProfile profile) {
            goalRevisions.add(profile);
        }
    }

    static final class Consents implements ConsentStorePort {
        final List<Consent> journal = new ArrayList<>();

        @Override
        public void append(Consent consent) {
            journal.add(consent);
        }

        @Override
        public List<Consent> latestPerType(UUID userId) {
            Map<ConsentType, Consent> latest = journal.stream()
                    .filter(c -> c.userId().equals(userId))
                    .collect(Collectors.toMap(
                            Consent::type, c -> c, (a, b) -> b.recordedAt().isBefore(a.recordedAt()) ? a : b));
            return latest.values().stream()
                    .sorted(Comparator.comparing(Consent::type))
                    .toList();
        }
    }

    static final class Requests implements PrivacyRequestStorePort {
        final List<DataExport> exports = new ArrayList<>();
        final List<AccountDeletion> deletions = new ArrayList<>();

        @Override
        public Optional<DataExport> latestExport(UUID userId) {
            return exports.stream().filter(e -> e.userId().equals(userId)).reduce((a, b) -> b);
        }

        @Override
        public Optional<DataExport> findExport(UUID userId, UUID exportId) {
            return exports.stream()
                    .filter(e -> e.userId().equals(userId) && e.id().equals(exportId))
                    .findFirst();
        }

        @Override
        public void saveExport(DataExport export) {
            exports.add(export);
        }

        @Override
        public Optional<AccountDeletion> latestDeletion(UUID userId) {
            return deletions.stream().filter(d -> d.userId().equals(userId)).reduce((a, b) -> b);
        }

        @Override
        public void saveDeletion(AccountDeletion deletion) {
            deletions.add(deletion);
        }
    }

    static final class Events implements ProfileEventPublisherPort {
        final List<String> published = new ArrayList<>();

        @Override
        public void profileCreated(UserProfile profile, boolean emailVerified) {
            published.add("profile-created");
        }

        @Override
        public void onboardingCompleted(UserProfile profile, Instant occurredAt) {
            published.add("onboarding-completed:" + profile.goalRevision());
        }

        @Override
        public void preferencesUpdated(UserProfile profile, Instant occurredAt) {
            published.add("preferences-updated");
        }

        @Override
        public void consentChanged(Consent consent) {
            published.add("consent-changed:" + consent.type());
        }

        @Override
        public void accountDeletionRequested(AccountDeletion deletion, Set<Role> roles) {
            published.add("account-deletion-requested");
        }
    }

    static final class Identity implements IdentityProviderPort, SessionRevocationPort {
        final List<String> calls = new ArrayList<>();
        boolean failRevocation;

        @Override
        public void makeTutor(UUID userId) {
            calls.add("makeTutor");
        }

        @Override
        public void disableUser(UUID userId) {
            calls.add("disableUser");
        }

        @Override
        public void revokeSessions(UUID userId) {
            if (failRevocation) {
                throw new IllegalStateException("gateway down");
            }
            calls.add("revokeSessions");
        }
    }
}
