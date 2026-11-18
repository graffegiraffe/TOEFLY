package com.toefly.assessment.adapter.out.persistence;

import com.toefly.assessment.application.port.out.EstimateInputsRepository;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.EstimateInputs;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.estimate.WeeklyEstimate;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Projections of the estimate ({@code ability_snapshot}, {@code build_sentence_result}, {@code learner_onboarding})
 * and its snapshots ({@code band_estimate}, {@code band_estimate_week}) as JSON.
 */
@Repository
public class JdbcEstimateInputsAdapter implements EstimateInputsRepository {

    static final int BUILD_SENTENCE_ITEMS = 30;
    private static final TypeReference<List<Boolean>> BOOLEANS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcEstimateInputsAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void saveAbility(UUID userId, Section section, double theta, double se, int n, Instant at) {
        jdbc.sql("""
                        INSERT INTO ability_snapshot (user_id, section, theta, standard_error, n, updated_at)
                        VALUES (:u, :s, :theta, :se, :n, :at)
                        ON CONFLICT (user_id, section) DO UPDATE SET theta = EXCLUDED.theta,
                            standard_error = EXCLUDED.standard_error, n = EXCLUDED.n, updated_at = EXCLUDED.updated_at
                        WHERE ability_snapshot.updated_at <= EXCLUDED.updated_at
                        """)
                .param("u", userId)
                .param("s", section.name())
                .param("theta", theta)
                .param("se", se)
                .param("n", n)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void addBuildSentenceItems(UUID userId, UUID attemptId, List<Boolean> correct, Instant at) {
        jdbc.sql("""
                        INSERT INTO build_sentence_result (user_id, attempt_id, correct, scored_at)
                        VALUES (:u, :a, :c::jsonb, :at) ON CONFLICT DO NOTHING
                        """)
                .param("u", userId)
                .param("a", attemptId)
                .param("c", json.writeValueAsString(correct))
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void onboardingCompleted(UUID userId, Instant at) {
        jdbc.sql("""
                        INSERT INTO learner_onboarding (user_id, completed_at) VALUES (:u, :at)
                        ON CONFLICT (user_id) DO NOTHING
                        """).param("u", userId).param("at", Timestamp.from(at)).update();
    }

    @Override
    public EstimateInputs load(UUID userId, Instant asOf) {
        Map<Section, EstimateInputs.Ability> abilities = new EnumMap<>(Section.class);
        jdbc.sql("SELECT * FROM ability_snapshot WHERE user_id = :u")
                .param("u", userId)
                .query((rs, n) -> abilities.put(
                        Section.valueOf(rs.getString("section")),
                        new EstimateInputs.Ability(
                                rs.getDouble("theta"), rs.getDouble("standard_error"), rs.getInt("n"))))
                .list();
        List<Boolean> items = new ArrayList<>();
        for (String row : jdbc.sql("""
                        SELECT correct::text FROM build_sentence_result WHERE user_id = :u
                        ORDER BY scored_at DESC LIMIT :l
                        """)
                .param("u", userId)
                .param("l", BUILD_SENTENCE_ITEMS)
                .query(String.class)
                .list()) {
            items.addAll(json.readValue(row, BOOLEANS));
        }
        Instant onboarded = jdbc.sql("SELECT completed_at FROM learner_onboarding WHERE user_id = :u")
                .param("u", userId)
                .query((rs, n) -> rs.getTimestamp(1).toInstant())
                .optional()
                .orElse(null);
        return new EstimateInputs(
                onboarded,
                abilities,
                items.subList(0, Math.min(items.size(), BUILD_SENTENCE_ITEMS)),
                List.of(),
                List.of(),
                asOf);
    }

    @Override
    public void saveEstimate(UUID userId, BandEstimate estimate, LocalDate weekStart) {
        String body = json.writeValueAsString(estimate);
        jdbc.sql("""
                        INSERT INTO band_estimate (user_id, estimate, updated_at) VALUES (:u, :e::jsonb, :at)
                        ON CONFLICT (user_id) DO UPDATE SET estimate = EXCLUDED.estimate, updated_at = EXCLUDED.updated_at
                        """)
                .param("u", userId)
                .param("e", body)
                .param("at", Timestamp.from(estimate.asOf()))
                .update();
        jdbc.sql("""
                        INSERT INTO band_estimate_week (user_id, week_start, estimate) VALUES (:u, :w, :e::jsonb)
                        ON CONFLICT (user_id, week_start) DO UPDATE SET estimate = EXCLUDED.estimate
                        """)
                .param("u", userId)
                .param("w", Date.valueOf(weekStart))
                .param("e", body)
                .update();
    }

    @Override
    public Optional<BandEstimate> current(UUID userId) {
        return jdbc.sql("SELECT estimate::text FROM band_estimate WHERE user_id = :u")
                .param("u", userId)
                .query(String.class)
                .optional()
                .map(body -> json.readValue(body, BandEstimate.class));
    }

    @Override
    public List<WeeklyEstimate> history(UUID userId, int limit) {
        return jdbc.sql("""
                        SELECT week_start, estimate::text AS e FROM band_estimate_week WHERE user_id = :u
                        ORDER BY week_start DESC LIMIT :l
                        """)
                .param("u", userId)
                .param("l", limit)
                .query((rs, n) -> new WeeklyEstimate(
                        rs.getDate("week_start").toLocalDate(), json.readValue(rs.getString("e"), BandEstimate.class)))
                .list();
    }
}
