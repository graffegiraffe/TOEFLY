package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.composition.DraftLayout.Day;
import com.toefly.planning.domain.composition.DraftLayout.Pick;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The checks of a draft of WeekGenerator (docs/06-ai-agent.md §5.2) and the rules of the week the composition obeys
 * (docs/03 §10.2–10.3, INV-WK-02, INV-WK-08). Every violation is an English sentence the model gets back on the
 * retry.
 *
 * <ul>
 *   <li>every ref is a candidate and is used once; every mandatory candidate is used;
 *   <li>each day stays within ±15% of the daily minutes; the lower bound shrinks to the minutes the other tasks can
 *       give a day once each book unit has a day (a pool of short closed sets cannot fill an hour), and a task longer
 *       than the upper bound stays alone;
 *   <li>the share of each section stays within ±0.07 of the week of the rules;
 *   <li>the week is not longer than the budget (or the week of the rules) and not lighter than 90% of the week of the
 *       rules;
 *   <li>at most 2 tasks of a type a day (AC-04.03.1); at most 2 Writing or Speaking tasks a day unless the section
 *       has more than 14 in the week (the short closed Reading and Listening sets are bound by the type rule only), one vocabulary review a day, no Writing or Speaking on the day of the mock exam.
 * </ul>
 */
public final class WeekDraftValidator {

    static final double DAY_TOLERANCE = 0.15;
    static final double BIG_TASK = 0.75;
    static final double SECTION_TOLERANCE = 0.07;
    static final double MIN_SHARE_OF_RULES_WEEK = 0.9;
    static final int SAME_TYPE_PER_DAY = 2;
    static final int SAME_SECTION_PER_DAY = 2;
    private static final double EPS = 1e-6;

    private final CandidatePool pool;
    private final DraftLayout layout;
    private final LocalDate day1;
    private final int minutesPerDay;
    private final double budgetMax;
    private final List<String> violations = new ArrayList<>();
    private final Map<Integer, List<WeekCandidate>> byDay = new HashMap<>();
    private final Map<WeekCandidate, String> why = new HashMap<>();

    private WeekDraftValidator(
            CandidatePool pool, DraftLayout layout, LocalDate day1, int minutesPerDay, double budgetMax) {
        this.pool = pool;
        this.layout = layout;
        this.day1 = day1;
        this.minutesPerDay = minutesPerDay;
        this.budgetMax = budgetMax;
        for (int day = 1; day <= Week.DAYS; day++) {
            byDay.put(day, new ArrayList<>());
        }
    }

    /** Violations of the draft; empty when the week may be published. */
    public static List<String> validate(
            CandidatePool pool, DraftLayout layout, LocalDate day1, int minutesPerDay, double budgetMax) {
        WeekDraftValidator validator = new WeekDraftValidator(pool, layout, day1, minutesPerDay, budgetMax);
        validator.check();
        return List.copyOf(validator.violations);
    }

    /**
     * The tasks of a valid draft on their days with the "why for you" of the model (the template when the model left
     * it empty).
     *
     * @throws IllegalArgumentException when the draft has violations
     */
    public static List<WeekTask> tasks(
            CandidatePool pool, DraftLayout layout, LocalDate day1, int minutesPerDay, double budgetMax) {
        WeekDraftValidator validator = new WeekDraftValidator(pool, layout, day1, minutesPerDay, budgetMax);
        validator.check();
        if (!validator.violations.isEmpty()) {
            throw new IllegalArgumentException("The draft breaks the rules of the week: " + validator.violations);
        }
        List<WeekTask> tasks = new ArrayList<>();
        validator.byDay.forEach((day, candidates) -> {
            for (WeekCandidate candidate : candidates) {
                String text = validator.why.getOrDefault(candidate, "").strip();
                tasks.add(candidate
                        .task()
                        .laidOut(day, text.isEmpty() ? candidate.task().rationale() : GreedyWeekPacker.cut(text)));
            }
        });
        return tasks;
    }

    /** Day of the week {@code 1..7} of {@code MON}…{@code SUN} for a week from {@code day1}; 0 for another name. */
    public static int dayNo(LocalDate day1, String name) {
        for (int day = 1; day <= Week.DAYS; day++) {
            if (dayName(day1.plusDays(day - 1L).getDayOfWeek()).equals(name)) {
                return day;
            }
        }
        return 0;
    }

    /** {@code MON} for Monday. */
    public static String dayName(DayOfWeek weekday) {
        return weekday.name().substring(0, 3);
    }

    /**
     * The daily minutes the model has to keep, {@code [lower, upper]}: at most 15% over the daily minutes; at least
     * 15% under the average day the other tasks can fill once every big task (a book unit: at least 3/4 of a day)
     * has a day of its own.
     */
    public static double[] dayBounds(int minutesPerDay, List<Double> chosenMinutes) {
        double upper = (1 + DAY_TOLERANCE) * minutesPerDay;
        double total = 0;
        double big = 0;
        int bigCount = 0;
        for (double minutes : chosenMinutes) {
            total += minutes;
            if (minutes >= BIG_TASK * minutesPerDay - EPS) {
                big += minutes;
                bigCount++;
            }
        }
        int days = Math.max(1, Week.DAYS - bigCount);
        double lower = (1 - DAY_TOLERANCE) * Math.min(minutesPerDay, (total - big) / days);
        return new double[] {lower, upper};
    }

    // ---- checks ----

    private void check() {
        Set<String> used = new HashSet<>();
        Set<String> dayNames = new HashSet<>();
        for (Day day : layout.days()) {
            int dayNo = dayNo(day1, day.day());
            if (dayNo == 0) {
                violations.add("Day " + day.day() + " is not a study day of this week.");
                continue;
            }
            if (!dayNames.add(day.day())) {
                violations.add("Day " + day.day() + " is listed twice; list each day once.");
            }
            for (Pick pick : day.tasks()) {
                var candidate = pool.byRef(pick.candidateRef());
                if (candidate.isEmpty()) {
                    violations.add(pick.candidateRef() + " is not a candidate; use only the refs of the list.");
                } else if (!used.add(pick.candidateRef())) {
                    violations.add(pick.candidateRef() + " is used more than once; use each ref once.");
                } else {
                    byDay.get(dayNo).add(candidate.get());
                    why.put(candidate.get(), pick.whyForYou());
                }
            }
        }
        for (String ref : pool.mandatoryRefs()) {
            if (!used.contains(ref)) {
                violations.add("Mandatory " + ref + " is missing; include every mandatory candidate.");
            }
        }
        List<WeekCandidate> chosen =
                byDay.values().stream().flatMap(List::stream).toList();
        if (chosen.isEmpty()) {
            violations.add("The week has no tasks.");
            return;
        }
        checkTotal(chosen);
        checkDays(chosen);
        checkSections(chosen);
    }

    private void checkTotal(List<WeekCandidate> chosen) {
        double total = minutes(chosen);
        double baseline = pool.baselineMinutes();
        double most = Math.max(budgetMax, baseline);
        double least = MIN_SHARE_OF_RULES_WEEK * baseline;
        if (total > most + EPS) {
            violations.add("The week has %s minutes; keep it at most %s.".formatted(round(total), round(most)));
        } else if (total < least - EPS) {
            violations.add("The week has %s minutes; keep it at least %s.".formatted(round(total), round(least)));
        }
    }

    private void checkDays(List<WeekCandidate> chosen) {
        double[] bounds = dayBounds(
                minutesPerDay, chosen.stream().map(WeekCandidate::minutes).toList());
        double lower = bounds[0];
        double upper = bounds[1];
        Map<WeekSection, Integer> perSection = new EnumMap<>(WeekSection.class);
        for (WeekCandidate candidate : chosen) {
            if (candidate.task().kind() != WeekTaskKind.VOCABULARY_REVIEW) {
                perSection.merge(candidate.task().section(), 1, Integer::sum);
            }
        }
        for (int day = 1; day <= Week.DAYS; day++) {
            String name = dayName(day1.plusDays(day - 1L).getDayOfWeek());
            List<WeekCandidate> tasks = byDay.get(day);
            double load = minutes(tasks);
            boolean oversized = tasks.stream().anyMatch(task -> task.minutes() > upper + EPS);
            if (oversized && tasks.size() > 1) {
                violations.add(name + " has a task longer than " + round(upper) + " minutes; put it alone on its day.");
            } else if (!oversized && (load > upper + EPS || load < lower - EPS)) {
                violations.add("%s has %s minutes; keep each day between %s and %s."
                        .formatted(name, round(load), round(lower), round(upper)));
            }
            Map<TaskType, Integer> types = new EnumMap<>(TaskType.class);
            Map<WeekSection, Integer> sections = new EnumMap<>(WeekSection.class);
            int reviews = 0;
            boolean mock = false;
            boolean writingOrSpeaking = false;
            for (WeekCandidate candidate : tasks) {
                WeekTask task = candidate.task();
                if (task.taskType() != null) {
                    types.merge(task.taskType(), 1, Integer::sum);
                }
                if (task.kind() == WeekTaskKind.VOCABULARY_REVIEW) {
                    reviews++;
                } else if (task.section() == WeekSection.WRITING || task.section() == WeekSection.SPEAKING) {
                    sections.merge(task.section(), 1, Integer::sum);
                }
                mock |= task.kind() == WeekTaskKind.MOCK_EXAM;
                writingOrSpeaking |= task.kind() != WeekTaskKind.MOCK_EXAM
                        && (task.section() == WeekSection.WRITING || task.section() == WeekSection.SPEAKING);
            }
            types.forEach((type, count) -> {
                if (count > SAME_TYPE_PER_DAY) {
                    violations.add("%s has %d %s tasks; at most %d of one type a day."
                            .formatted(name, count, type.label(), SAME_TYPE_PER_DAY));
                }
            });
            sections.forEach((section, count) -> {
                int most =
                        Math.max(SAME_SECTION_PER_DAY, (int) Math.ceil(perSection.get(section) / (double) Week.DAYS));
                if (count > most) {
                    violations.add("%s has %d %s tasks; at most %d of one section a day."
                            .formatted(name, count, section, most));
                }
            });
            if (reviews > 1) {
                violations.add(name + " has " + reviews + " vocabulary reviews; at most one a day.");
            }
            if (mock && writingOrSpeaking) {
                violations.add(name + " has the mock exam; move the Writing and Speaking tasks to another day.");
            }
        }
    }

    private void checkSections(List<WeekCandidate> chosen) {
        double total = minutes(chosen);
        Map<WeekSection, Double> weights = pool.sectionWeights();
        Map<WeekSection, Double> shares = new EnumMap<>(WeekSection.class);
        chosen.forEach(candidate -> shares.merge(candidate.task().section(), candidate.minutes() / total, Double::sum));
        Set<WeekSection> sections = EnumSet.noneOf(WeekSection.class);
        sections.addAll(weights.keySet());
        sections.addAll(shares.keySet());
        for (WeekSection section : sections) {
            double weight = weights.getOrDefault(section, 0.0);
            double share = shares.getOrDefault(section, 0.0);
            if (Math.abs(share - weight) > SECTION_TOLERANCE + EPS) {
                violations.add(String.format(
                        Locale.ROOT,
                        "%s has %.2f of the minutes; keep it between %.2f and %.2f.",
                        section,
                        share,
                        Math.max(0, weight - SECTION_TOLERANCE),
                        Math.min(1, weight + SECTION_TOLERANCE)));
            }
        }
    }

    private static double minutes(List<WeekCandidate> candidates) {
        return candidates.stream().mapToDouble(WeekCandidate::minutes).sum();
    }

    private static String round(double minutes) {
        return String.valueOf(Math.round(minutes));
    }
}
