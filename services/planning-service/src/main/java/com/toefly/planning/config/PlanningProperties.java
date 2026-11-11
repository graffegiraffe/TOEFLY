package com.toefly.planning.config;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code toefly.planning.*}: the switches of this release and the composition of a week ({@code
 * week-composition.yaml}).
 */
@ConfigurationProperties("toefly.planning")
public record PlanningProperties(
        String contentUrl,
        Set<TaskType> availableTaskTypes,
        @DefaultValue("false") boolean vocabularyReviewEnabled,
        @DefaultValue("false") boolean mockExamEnabled,
        @DefaultValue("TOEFL_2026") String examFormat,
        List<Resource> resources,
        Rollover rollover,
        Composition composition) {

    public record Resource(String url, String title, Set<WeekSection> sections) {}

    public record Rollover(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT5M") Duration interval) {}

    public record Composition(
            int baseMinutesPerDay,
            Map<TaskType, Integer> baseCounts,
            Map<TaskType, Double> minutes,
            double weakFactor,
            double budgetMaxFactor,
            double budgetMinFactor,
            double unitMinutes,
            double vocabularyReviewMinutes,
            double resourceMinutes,
            double mockExamMinutes,
            int minimalModeMinutes) {}
}
