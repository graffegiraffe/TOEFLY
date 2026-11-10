package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Weak task types of the last two weeks (docs/02 §4.0): accuracy on closed items under 60%, or the band of the
 * section more than 0.5 under the target; then the least accurate type of that section is weak.
 */
public final class WeakTypes {

    public static final double ACCURACY_THRESHOLD = 0.60;
    public static final double BAND_MARGIN = 0.5;
    public static final int MIN_ITEMS = 3;

    private WeakTypes() {}

    /** Correct and total closed items of a type. */
    public record Accuracy(int correct, int total) {
        public double ratio() {
            return total == 0 ? 1 : correct / (double) total;
        }
    }

    /**
     * @param accuracy closed items of each type in the last two weeks
     * @param theta current θ of the exam sections that have one
     * @param targets target band by section name ({@code OVERALL} applies to sections without their own)
     * @return weak types, the weakest first
     */
    public static List<TaskType> detect(
            Map<TaskType, Accuracy> accuracy, Map<WeekSection, Double> theta, Map<String, Double> targets) {
        Set<TaskType> weak = new LinkedHashSet<>();
        accuracy.forEach((type, value) -> {
            if (value.total() >= MIN_ITEMS && value.ratio() < ACCURACY_THRESHOLD) {
                weak.add(type);
            }
        });
        theta.forEach((section, value) -> {
            Double target = targets.getOrDefault(section.name(), targets.get("OVERALL"));
            if (target != null && BandScale.band(value) < target - BAND_MARGIN) {
                accuracy.entrySet().stream()
                        .filter(entry -> entry.getKey().section() == section
                                && entry.getValue().total() > 0)
                        .min(Comparator.comparingDouble(
                                entry -> entry.getValue().ratio()))
                        .ifPresent(entry -> weak.add(entry.getKey()));
            }
        });
        List<TaskType> ordered = new ArrayList<>(weak);
        ordered.sort(Comparator.comparingDouble((TaskType type) ->
                        accuracy.getOrDefault(type, new Accuracy(0, 0)).ratio())
                .thenComparingInt(TaskType::ordinal));
        return List.copyOf(ordered);
    }
}
