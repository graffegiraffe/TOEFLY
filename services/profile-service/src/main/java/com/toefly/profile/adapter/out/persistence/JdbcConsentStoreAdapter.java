package com.toefly.profile.adapter.out.persistence;

import com.toefly.profile.application.port.out.ConsentStorePort;
import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConsentStoreAdapter implements ConsentStorePort {

    private final JdbcClient jdbc;

    public JdbcConsentStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(Consent consent) {
        jdbc.sql("""
                        INSERT INTO consent (id, user_id, consent_type, granted, policy_version, source, ip_prefix,
                            recorded_at)
                        VALUES (:id, :userId, :type, :granted, :policyVersion, :source, :ipPrefix, :recordedAt)
                        """)
                .param("id", consent.id())
                .param("userId", consent.userId())
                .param("type", consent.type().name())
                .param("granted", consent.granted())
                .param("policyVersion", consent.policyVersion())
                .param("source", consent.source().name())
                .param("ipPrefix", consent.ipPrefix())
                .param("recordedAt", Timestamp.from(consent.recordedAt()))
                .update();
    }

    @Override
    public List<Consent> latestPerType(UUID userId) {
        return jdbc.sql("""
                        SELECT DISTINCT ON (consent_type) id, user_id, consent_type, granted, policy_version, source,
                            ip_prefix, recorded_at
                        FROM consent WHERE user_id = :userId
                        ORDER BY consent_type, recorded_at DESC, id
                        """)
                .param("userId", userId)
                .query((rs, row) -> new Consent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        ConsentType.valueOf(rs.getString("consent_type")),
                        rs.getBoolean("granted"),
                        rs.getString("policy_version"),
                        ConsentSource.valueOf(rs.getString("source")),
                        rs.getString("ip_prefix"),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }
}
