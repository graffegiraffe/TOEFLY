package com.toefly.assessment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.assessment.application.port.in.IdempotencyKeyReuseException;
import com.toefly.assessment.application.port.in.RequestInProgressException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {

    private final Fakes.Idempotency store = new Fakes.Idempotency();
    private final IdempotencyService service = new IdempotencyService(store, new Fakes().clock);
    private final UUID user = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();

    @Test
    @DisplayName("a repeated POST with the same key replays the stored response")
    void replays() {
        assertThat(service.begin(user, key, "submit", "h")).isEmpty();
        assertThatThrownBy(() -> service.begin(user, key, "submit", "h"))
                .isInstanceOf(RequestInProgressException.class);
        service.complete(user, key, 202, "{}", "/x");
        assertThat(service.begin(user, key, "submit", "h"))
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(202));
        assertThatThrownBy(() -> service.begin(user, key, "submit", "other"))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        service.release(user, key);
        assertThat(service.begin(user, key, "submit", "other")).isEmpty();
    }
}
