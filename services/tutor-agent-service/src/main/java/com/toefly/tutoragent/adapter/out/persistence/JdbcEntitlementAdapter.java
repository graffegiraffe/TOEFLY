package com.toefly.tutoragent.adapter.out.persistence;

import com.toefly.tutoragent.application.port.out.EntitlementPort;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Rights from the entitlement_view projection. No row yet means the rights of STUDENT_FREE (billing-service plan tiers):
 * the weekly review is free, the chat needs Pro (docs/01 §6.1).
 */
@Component
public class JdbcEntitlementAdapter implements EntitlementPort {

    static final Set<String> FREE = Set.of("AI_WRITING_CHECK", "AI_SPEAKING_CHECK", "SPEAKING", "WEEKLY_REVIEW");

    private final JdbcClient jdbc;

    public JdbcEntitlementAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean has(UUID userId, String entitlement) {
        return jdbc.sql("SELECT :e = ANY (features) FROM entitlement_view WHERE user_id = :u")
                .param("e", entitlement)
                .param("u", userId)
                .query(Boolean.class)
                .optional()
                .orElseGet(() -> FREE.contains(entitlement));
    }
}
