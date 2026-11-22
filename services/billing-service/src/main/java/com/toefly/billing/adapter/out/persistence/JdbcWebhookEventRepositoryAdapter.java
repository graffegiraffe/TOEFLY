package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.application.port.out.WebhookEventRepository;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The payload is the provider-neutral event: the minimal subset of fields (docs/10 §17.4), never card data. */
@Repository
public class JdbcWebhookEventRepositoryAdapter implements WebhookEventRepository {

    static final Map<String, Class<? extends ProviderEvent>> KINDS = Map.of(
            "CheckoutCompleted", ProviderEvent.CheckoutCompleted.class,
            "CheckoutFailed", ProviderEvent.CheckoutFailed.class,
            "RenewalPaid", ProviderEvent.RenewalPaid.class,
            "RenewalFailed", ProviderEvent.RenewalFailed.class,
            "SubscriptionEnded", ProviderEvent.SubscriptionEnded.class,
            "Refunded", ProviderEvent.Refunded.class,
            "Ignored", ProviderEvent.Ignored.class);

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcWebhookEventRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insertIfAbsent(PaymentProviderCode provider, ProviderEvent event, Instant receivedAt) {
        String payload = json.writeValueAsString(
                Map.of("kind", event.getClass().getSimpleName(), "event", json.convertValue(event, Map.class)));
        return jdbc.sql("""
                                INSERT INTO webhook_event (provider, provider_event_id, type, payload, received_at)
                                VALUES (:provider, :id, :type, :payload::jsonb, :at)
                                ON CONFLICT (provider, provider_event_id) DO NOTHING
                                """)
                        .param("provider", provider.name())
                        .param("id", event.eventId())
                        .param("type", truncate(event.type(), 60))
                        .param("payload", payload)
                        .param("at", Timestamp.from(receivedAt))
                        .update()
                == 1;
    }

    @Override
    public List<Stored> lockPending(int limit, Instant now) {
        return jdbc.sql("""
                        SELECT provider, payload::text AS payload, attempts FROM webhook_event
                        WHERE processed_at IS NULL AND received_at <= :now
                        ORDER BY received_at LIMIT :limit FOR UPDATE SKIP LOCKED
                        """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public Optional<Stored> lock(PaymentProviderCode provider, String eventId) {
        return jdbc.sql("""
                        SELECT provider, payload::text AS payload, attempts FROM webhook_event
                        WHERE provider = :provider AND provider_event_id = :id AND processed_at IS NULL
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("provider", provider.name())
                .param("id", eventId)
                .query(this::map)
                .optional();
    }

    @Override
    public void markProcessed(PaymentProviderCode provider, String eventId, Instant now) {
        jdbc.sql("UPDATE webhook_event SET processed_at = :now, last_error = NULL "
                        + "WHERE provider = :provider AND provider_event_id = :id")
                .param("now", Timestamp.from(now))
                .param("provider", provider.name())
                .param("id", eventId)
                .update();
    }

    /** {@code received_at} moves forward so the poller waits before the next attempt. */
    @Override
    public void markFailed(PaymentProviderCode provider, String eventId, String error, Instant retryAfter) {
        jdbc.sql("""
                        UPDATE webhook_event SET attempts = attempts + 1, last_error = :error, received_at = :retry
                        WHERE provider = :provider AND provider_event_id = :id
                        """)
                .param("error", truncate(error, 2000))
                .param("retry", Timestamp.from(retryAfter))
                .param("provider", provider.name())
                .param("id", eventId)
                .update();
    }

    @SuppressWarnings("unchecked")
    private Stored map(ResultSet rs, int row) throws SQLException {
        Map<String, Object> payload = json.readValue(rs.getString("payload"), Map.class);
        Class<? extends ProviderEvent> kind = KINDS.get((String) payload.get("kind"));
        if (kind == null) {
            throw new SQLException("unknown webhook event kind " + payload.get("kind"));
        }
        return new Stored(
                PaymentProviderCode.valueOf(rs.getString("provider")),
                json.convertValue(payload.get("event"), kind),
                rs.getInt("attempts"));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
