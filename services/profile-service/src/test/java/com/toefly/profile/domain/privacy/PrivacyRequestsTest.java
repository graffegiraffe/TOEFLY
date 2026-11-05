package com.toefly.profile.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrivacyRequestsTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("AC-31.03.3 a new export is rejected within 24 hours with the time to wait")
    void exportAtMostOncePerDay() {
        DataExport first = DataExport.request(USER, null, NOW);

        assertThat(first.status()).isEqualTo(DataExportStatus.REQUESTED);
        assertThatThrownBy(() -> DataExport.request(USER, first, NOW.plus(Duration.ofHours(23))))
                .isInstanceOf(DataExportTooSoonException.class)
                .satisfies(e -> assertThat(((DataExportTooSoonException) e).problemProperties())
                        .containsEntry("retryAfterSeconds", 3600L));
        assertThat(DataExport.request(USER, first, NOW.plus(Duration.ofHours(24)))
                        .id())
                .isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("AC-31.04.1 deletion needs DELETE and sets the 30-day deadline")
    void deletionNeedsConfirmation() {
        assertThatThrownBy(() -> AccountDeletion.request(USER, "delete", NOW, null, NOW))
                .isInstanceOf(DeletionNotConfirmedException.class);

        AccountDeletion deletion = AccountDeletion.request(USER, "DELETE", NOW.minusSeconds(60), null, NOW);

        assertThat(deletion.status()).isEqualTo(AccountDeletionStatus.REQUESTED);
        assertThat(deletion.legalDeadlineAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("R-12 deletion needs a sign-in at most five minutes old")
    void deletionNeedsFreshSignIn() {
        assertThatThrownBy(() -> AccountDeletion.request(USER, "DELETE", NOW.minusSeconds(301), null, NOW))
                .isInstanceOf(ReauthenticationRequiredException.class);
        assertThatThrownBy(() -> AccountDeletion.request(USER, "DELETE", null, null, NOW))
                .isInstanceOf(ReauthenticationRequiredException.class);
    }

    @Test
    @DisplayName("INV-PRF-07 only one deletion is active")
    void oneActiveDeletion() {
        AccountDeletion active = AccountDeletion.request(USER, "DELETE", NOW, null, NOW);

        assertThatThrownBy(() -> AccountDeletion.request(USER, "DELETE", NOW, active, NOW))
                .isInstanceOf(AccountPendingDeletionException.class);
    }
}
