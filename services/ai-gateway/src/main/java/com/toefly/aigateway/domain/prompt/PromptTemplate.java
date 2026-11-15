package com.toefly.aigateway.domain.prompt;

import com.toefly.aigateway.domain.routing.AiUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import org.jspecify.annotations.Nullable;

/**
 * Root of a prompt (§2.9): one active version per key (INV-AIG-01) and an optional canary that a fixed share of
 * learners receives, chosen by {@code hash(userId) % 100}.
 */
public record PromptTemplate(
        String key,
        AiUseCase useCase,
        String description,
        String ownerService,
        @Nullable Integer activeVersion,
        @Nullable Integer canaryVersion,
        int canaryPercent) {

    public PromptTemplate {
        if (canaryPercent < 0 || canaryPercent > 100) {
            throw new PromptRuleException("validation-failed", "canaryPercent must be 0..100");
        }
    }

    /** Version a caller gets when it pins none; {@code null} if nothing is active yet. */
    public @Nullable Integer versionFor(@Nullable UUID userId) {
        if (canaryVersion != null && canaryPercent > 0 && userId != null && bucket(userId) < canaryPercent) {
            return canaryVersion;
        }
        return activeVersion;
    }

    public PromptTemplate activate(int version) {
        return new PromptTemplate(key, useCase, description, ownerService, version, null, 0);
    }

    public PromptTemplate canary(int version, int percent) {
        if (activeVersion == null) {
            throw new PromptRuleException("conflict", "a canary needs an active version of " + key);
        }
        return new PromptTemplate(key, useCase, description, ownerService, activeVersion, version, percent);
    }

    static int bucket(UUID userId) {
        CRC32 crc = new CRC32();
        crc.update(userId.toString().getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100);
    }
}
