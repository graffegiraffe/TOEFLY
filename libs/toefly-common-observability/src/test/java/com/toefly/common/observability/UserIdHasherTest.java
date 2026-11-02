package com.toefly.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class UserIdHasherTest {

    @Test
    void hashIsStableSaltedAndHidesTheId() {
        String userId = "7b1c9a44-0f3e-4f0b-9a60-6a53f1d2c871";

        String hash = new UserIdHasher("salt-a").hash(userId);

        assertThat(hash).hasSize(64).doesNotContain(userId).isEqualTo(new UserIdHasher("salt-a").hash(userId));
        assertThat(new UserIdHasher("salt-b").hash(userId)).isNotEqualTo(hash);
    }

    @Test
    void requiresSalt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserIdHasher(" "));
    }
}
