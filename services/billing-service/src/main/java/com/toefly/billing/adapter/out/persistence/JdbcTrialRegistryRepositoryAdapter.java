package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.TrialRegistryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTrialRegistryRepositoryAdapter implements TrialRegistryRepository {

    private final JdbcClient jdbc;

    public JdbcTrialRegistryRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean register(String identityHash, Instant now, Instant since) {
        return jdbc.sql("""
                                INSERT INTO trial_registry (identity_hash, granted_at) VALUES (:hash, :now)
                                ON CONFLICT (identity_hash) DO UPDATE SET granted_at = EXCLUDED.granted_at
                                WHERE trial_registry.granted_at < :since
                                """)
                        .param("hash", identityHash)
                        .param("now", Timestamp.from(now))
                        .param("since", Timestamp.from(since))
                        .update()
                == 1;
    }
}
