package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.metrics.LearnerMilestones;
import com.toefly.analytics.domain.metrics.ProductCounters;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code product_metrics_daily} and the funnel steps of each learner. */
public interface ProductMetricsRepository {

    Optional<LearnerMilestones> milestones(UUID userId);

    void saveMilestones(LearnerMilestones milestones);

    List<LearnerMilestones> allMilestones();

    void add(LocalDate day, ProductCounters.Counter counter, int delta);

    List<ProductCounters> counters(LocalDate from, LocalDate to);

    void reset();

    /** Removes the funnel steps of the learner; the daily counters have no identifiers and stay. */
    void deleteUser(UUID userId);
}
