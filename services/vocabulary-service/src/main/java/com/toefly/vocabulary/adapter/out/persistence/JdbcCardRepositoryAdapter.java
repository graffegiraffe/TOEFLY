package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.in.LapsedEntry;
import com.toefly.vocabulary.application.port.out.CardRepository;
import com.toefly.vocabulary.domain.card.ReviewCard;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** review_card (docs/03 §6.11). */
@Repository
public class JdbcCardRepositoryAdapter implements CardRepository {

    private static final String SELECT = "SELECT " + Rows.CARD_COLUMNS + " FROM review_card c ";

    private final JdbcClient jdbc;

    public JdbcCardRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReviewCard> find(UUID userId, UUID cardId) {
        return jdbc.sql(SELECT + "WHERE c.user_id = :user AND c.id = :id")
                .param("user", userId)
                .param("id", cardId)
                .query((rs, row) -> Rows.card(rs))
                .optional();
    }

    @Override
    public Optional<ReviewCard> findForUpdate(UUID userId, UUID cardId) {
        return jdbc.sql(SELECT + "WHERE c.user_id = :user AND c.id = :id FOR UPDATE")
                .param("user", userId)
                .param("id", cardId)
                .query((rs, row) -> Rows.card(rs))
                .optional();
    }

    @Override
    public Optional<ReviewCard> findByEntry(UUID entryId) {
        return jdbc.sql(SELECT + "WHERE c.entry_id = :entry")
                .param("entry", entryId)
                .query((rs, row) -> Rows.card(rs))
                .optional();
    }

    @Override
    public List<ReviewCard> findAll(UUID userId, Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + "WHERE c.user_id = :user AND c.id IN (:ids)")
                .param("user", userId)
                .param("ids", cardIds)
                .query((rs, row) -> Rows.card(rs))
                .list();
    }

    @Override
    public List<ReviewCard> queueCandidates(UUID userId, Instant dueBefore) {
        return jdbc.sql(SELECT + """
                        WHERE c.user_id = :user AND NOT c.suspended AND (c.state = 'NEW' OR c.due < :due)
                        ORDER BY c.due, c.created_at
                        """)
                .param("user", userId)
                .param("due", Sql.ts(dueBefore))
                .query((rs, row) -> Rows.card(rs))
                .list();
    }

    @Override
    public void insert(ReviewCard card) {
        jdbc.sql("""
                        INSERT INTO review_card (id, entry_id, user_id, state, due, stability, difficulty, reps,
                            lapses, last_review_at, learning_step, suspended, fsrs_params_version, created_at, version)
                        VALUES (:id, :entry, :user, :state, :due, :stability, :difficulty, :reps, :lapses, :last,
                            :step, :suspended, :params, :created, 0)
                        """).paramSource(params(card)).update();
    }

    @Override
    public ReviewCard update(ReviewCard card) {
        int updated = jdbc.sql("""
                        UPDATE review_card SET state = :state, due = :due, stability = :stability,
                            difficulty = :difficulty, reps = :reps, lapses = :lapses, last_review_at = :last,
                            learning_step = :step, suspended = :suspended, fsrs_params_version = :params,
                            version = version + 1
                        WHERE id = :id AND user_id = :user AND version = :version
                        """)
                .paramSource(params(card).addValue("version", card.version()))
                .update();
        if (updated != 1) {
            throw new OptimisticLockingFailureException("review_card " + card.id() + " changed concurrently");
        }
        return new ReviewCard(
                card.id(),
                card.entryId(),
                card.userId(),
                card.memory(),
                card.suspended(),
                card.paramsVersion(),
                card.createdAt(),
                card.version() + 1);
    }

    @Override
    public long countDue(UUID userId, Instant dueBefore) {
        return jdbc.sql("""
                        SELECT count(*) FROM review_card
                        WHERE user_id = :user AND NOT suspended AND state <> 'NEW' AND due < :due
                        """)
                .param("user", userId)
                .param("due", Sql.ts(dueBefore))
                .query(Long.class)
                .single();
    }

    @Override
    public List<LapsedEntry> mostLapsed(UUID userId, int limit) {
        return jdbc.sql("""
                        SELECT e.id, e.lemma, c.lapses FROM review_card c JOIN vocabulary_entry e ON e.id = c.entry_id
                        WHERE c.user_id = :user AND c.lapses > 0
                        ORDER BY c.lapses DESC, c.last_review_at DESC NULLS LAST LIMIT :limit
                        """)
                .param("user", userId)
                .param("limit", limit)
                .query((rs, row) ->
                        new LapsedEntry(Sql.requiredUuid(rs, "id"), rs.getString("lemma"), rs.getInt("lapses")))
                .list();
    }

    private static MapSqlParameterSource params(ReviewCard card) {
        return new MapSqlParameterSource()
                .addValue("id", card.id())
                .addValue("entry", card.entryId())
                .addValue("user", card.userId())
                .addValue("state", card.state().name())
                .addValue("due", Sql.ts(card.memory().due()))
                .addValue("stability", Sql.decimal(card.memory().stability()))
                .addValue("difficulty", Sql.decimal(card.memory().difficulty()))
                .addValue("reps", card.memory().reps())
                .addValue("lapses", card.memory().lapses())
                .addValue("last", Sql.ts(card.memory().lastReviewAt()))
                .addValue("step", card.memory().step())
                .addValue("suspended", card.suspended())
                .addValue("params", card.paramsVersion())
                .addValue("created", Sql.ts(card.createdAt()));
    }
}
