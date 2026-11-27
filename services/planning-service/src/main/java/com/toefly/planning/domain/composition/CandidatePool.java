package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The candidates of one week and the week the rules would build from them (docs/06-ai-agent.md §5.2).
 *
 * @param baseline tasks of GreedyWeekPacker: the fallback and the measure of the section weights
 */
public record CandidatePool(List<WeekCandidate> candidates, List<WeekTask> baseline) {

    public CandidatePool {
        candidates = List.copyOf(candidates);
        baseline = List.copyOf(baseline);
    }

    public Optional<WeekCandidate> byRef(String ref) {
        return candidates.stream()
                .filter(candidate -> candidate.ref().equals(ref))
                .findFirst();
    }

    public List<String> mandatoryRefs() {
        return candidates.stream()
                .filter(WeekCandidate::mandatory)
                .map(WeekCandidate::ref)
                .toList();
    }

    public double baselineMinutes() {
        return baseline.stream().mapToDouble(WeekTask::estimatedMinutes).sum();
    }

    /** Share of the minutes of each section in the week of the rules; the model keeps them within ±0.07. */
    public Map<WeekSection, Double> sectionWeights() {
        Map<WeekSection, Double> weights = new EnumMap<>(WeekSection.class);
        double total = baselineMinutes();
        if (total <= 0) {
            return weights;
        }
        for (WeekTask task : baseline) {
            weights.merge(task.section(), task.estimatedMinutes() / total, Double::sum);
        }
        return weights;
    }
}
