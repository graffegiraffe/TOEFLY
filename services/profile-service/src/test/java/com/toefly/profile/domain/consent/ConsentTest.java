package com.toefly.profile.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsentTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    @Test
    @DisplayName("AC-31.01.2 a consent keeps type, version, time, source and a truncated address")
    void recordsEvidence() {
        UUID userId = UUID.randomUUID();

        Consent consent = Consent.record(
                userId, ConsentType.AI_PROCESSING, true, "2026-11-01", ConsentSource.WEB, "203.0.113.77", NOW);

        assertThat(consent.userId()).isEqualTo(userId);
        assertThat(consent.policyVersion()).isEqualTo("2026-11-01");
        assertThat(consent.source()).isEqualTo(ConsentSource.WEB);
        assertThat(consent.ipPrefix()).isEqualTo("203.0.113.0/24");
        assertThat(consent.recordedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-31.01.1 optional consents can be withdrawn")
    void optionalConsentCanBeWithdrawn() {
        Consent consent = Consent.record(
                UUID.randomUUID(), ConsentType.MARKETING_EMAILS, false, "v1", ConsentSource.IOS, null, NOW);

        assertThat(consent.granted()).isFalse();
        assertThat(consent.ipPrefix()).isNull();
    }

    @Test
    @DisplayName("AC-31.01.1 Terms, Privacy and 16+ are required while the account exists")
    void requiredConsentCannotBeWithdrawn() {
        assertThatThrownBy(() ->
                        Consent.record(UUID.randomUUID(), ConsentType.TERMS, false, "v1", ConsentSource.WEB, null, NOW))
                .isInstanceOf(RequiredConsentException.class)
                .satisfies(e -> assertThat(((RequiredConsentException) e).problemProperties())
                        .containsEntry("consentType", "TERMS"));
        assertThat(ConsentType.REQUIRED_AT_SIGN_UP)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_16_PLUS);
    }

    @Test
    void truncatesIpv6ToSlash48() {
        assertThat(TruncatedIp.of("2001:db8:85a3:8d3:1319:8a2e:370:7348")).isEqualTo("2001:db8:85a3::/48");
        assertThat(TruncatedIp.of("::1")).isEqualTo("0:0:1::/48");
        assertThat(TruncatedIp.of("not-an-ip")).isNull();
        assertThat(TruncatedIp.of(" ")).isNull();
    }
}
