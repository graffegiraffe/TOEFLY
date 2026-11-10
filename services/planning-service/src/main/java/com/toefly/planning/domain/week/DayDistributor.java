package com.toefly.planning.domain.week;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spreads tasks over the days of a week (docs/03-domain-model.md §10.3): at most two tasks of one type a day, the
 * load of a day within ±25% of the daily minutes, no stacks of one type on neighbouring days.
 */
public final class DayDistributor {

    static final double DAY_TOLERANCE = 0.25;
    static final int SAME_TYPE_PER_DAY = 2;
    static final int ADJACENT_TYPE_PENALTY = 8;
    static final int SAME_SECTION_PENALTY = 6;
    static final int BALANCE_ITERATIONS = 20;

    private final LocalDate day1;
    private final double minutesPerDay;
    private final int firstDay;
    private final int lastDay;
    private final Map<Integer, List<WeekTask>> byDay = new HashMap<>();
    private final Map<Integer, Double> load = new HashMap<>();

    /**
     * @param fixed tasks already on their days that count in the load and the type limits but do not move
     */
    private DayDistributor(
            LocalDate day1, double minutesPerDay, int firstDay, int lastDay, Collection<WeekTask> fixed) {
        if (firstDay < 1 || lastDay < firstDay) {
            throw new IllegalArgumentException("Days " + firstDay + ".." + lastDay);
        }
        this.day1 = Objects.requireNonNull(day1);
        this.minutesPerDay = minutesPerDay;
        this.firstDay = firstDay;
        this.lastDay = lastDay;
        for (int day = firstDay; day <= lastDay; day++) {
            byDay.put(day, new ArrayList<>());
            load.put(day, 0.0);
        }
        for (WeekTask task : fixed) {
            if (task.dayNo() >= firstDay && task.dayNo() <= lastDay) {
                put(task, task.dayNo());
            }
        }
    }

    /** Assigns a day from {@code firstDay..lastDay} to every task of {@code movable}. */
    public static void distribute(
            LocalDate day1,
            double minutesPerDay,
            int firstDay,
            int lastDay,
            Collection<WeekTask> fixed,
            List<WeekTask> movable) {
        DayDistributor distributor = new DayDistributor(day1, minutesPerDay, firstDay, lastDay, fixed);
        distributor.place(movable);
        distributor.balance(movable);
    }

    private void place(List<WeekTask> movable) {
        List<WeekTask> mocks = new ArrayList<>();
        List<WeekTask> reviews = new ArrayList<>();
        List<WeekTask> rest = new ArrayList<>();
        for (WeekTask task : movable) {
            switch (task.kind()) {
                case MOCK_EXAM -> mocks.add(task);
                case VOCABULARY_REVIEW -> reviews.add(task);
                default -> rest.add(task);
            }
        }
        for (WeekTask mock : mocks) {
            put(mock, mockDay());
        }
        int day = firstDay;
        for (WeekTask review : reviews) {
            put(review, day);
            day = day == lastDay ? firstDay : day + 1;
        }
        rest.sort(Comparator.comparingDouble(WeekTask::estimatedMinutes)
                .reversed()
                .thenComparing(
                        task -> task.taskType() != null && task.taskType().open() ? 0 : 1));
        for (WeekTask task : rest) {
            put(task, bestDay(task));
        }
    }

    /** The weekend day with the most free time, else day 6 (or the last day of the range). */
    private int mockDay() {
        int best = -1;
        for (int day = firstDay; day <= lastDay; day++) {
            DayOfWeek weekday = day1.plusDays(day - 1L).getDayOfWeek();
            if ((weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY)
                    && (best < 0 || load.get(day) < load.get(best))) {
                best = day;
            }
        }
        return best > 0 ? best : Math.min(Math.max(6, firstDay), lastDay);
    }

    private int bestDay(WeekTask task) {
        double cap = (1 + DAY_TOLERANCE) * minutesPerDay;
        int best = -1;
        double bestPenalty = Double.MAX_VALUE;
        for (int pass = 0; pass < 3 && best < 0; pass++) {
            for (int day = firstDay; day <= lastDay; day++) {
                boolean typeOk = sameType(day, task.taskType()) < SAME_TYPE_PER_DAY;
                boolean fits = load.get(day) + task.estimatedMinutes() <= cap + 1e-9;
                boolean allowed = switch (pass) {
                    case 0 -> typeOk && fits;
                    case 1 -> typeOk;
                    default -> true;
                };
                if (!allowed) {
                    continue;
                }
                double penalty = penalty(day, task);
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    best = day;
                }
            }
        }
        return best;
    }

    private double penalty(int day, WeekTask task) {
        return load.get(day)
                + ADJACENT_TYPE_PENALTY * (sameType(day - 1, task.taskType()) + sameType(day + 1, task.taskType()))
                + SAME_SECTION_PENALTY * sameSection(day, task.section());
    }

    /**
     * Up to 20 moves or swaps towards the lightest day, from the heaviest donor that can give one; stops when no
     * move shrinks the gap.
     */
    private void balance(List<WeekTask> movable) {
        double cap = (1 + DAY_TOLERANCE) * minutesPerDay;
        for (int i = 0; i < BALANCE_ITERATIONS * (lastDay - firstDay + 1); i++) {
            int light = firstDay;
            for (int day = firstDay; day <= lastDay; day++) {
                if (load.get(day) < load.get(light)) {
                    light = day;
                }
            }
            final int lightest = light;
            List<Integer> donors = new ArrayList<>();
            for (int day = firstDay; day <= lastDay; day++) {
                if (day != lightest && load.get(day) > load.get(lightest) + 1e-9) {
                    donors.add(day);
                }
            }
            donors.sort(
                    Comparator.comparingDouble((Integer day) -> load.get(day)).reversed());
            boolean improved = false;
            for (int heavy : donors) {
                if (improve(heavy, lightest, load.get(heavy) - load.get(lightest), cap, movable)) {
                    improved = true;
                    break;
                }
            }
            if (!improved) {
                return;
            }
        }
    }

    private boolean improve(int heavy, int light, double gap, double cap, List<WeekTask> movable) {
        WeekTask bestMove = null;
        double bestGap = gap;
        for (WeekTask task : byDay.get(heavy)) {
            if (!balanceable(task, movable)) {
                continue;
            }
            double minutes = task.estimatedMinutes();
            double after = Math.abs((load.get(heavy) - minutes) - (load.get(light) + minutes));
            if (after < bestGap - 1e-9
                    && sameType(light, task.taskType()) < SAME_TYPE_PER_DAY
                    && load.get(light) + minutes <= cap + 1e-9) {
                bestGap = after;
                bestMove = task;
            }
        }
        if (bestMove != null) {
            move(bestMove, heavy, light);
            return true;
        }
        WeekTask fromHeavy = null;
        WeekTask fromLight = null;
        for (WeekTask a : byDay.get(heavy)) {
            if (!balanceable(a, movable)) {
                continue;
            }
            for (WeekTask b : byDay.get(light)) {
                if (!balanceable(b, movable) || a.estimatedMinutes() <= b.estimatedMinutes()) {
                    continue;
                }
                double delta = a.estimatedMinutes() - b.estimatedMinutes();
                double after = Math.abs((load.get(heavy) - delta) - (load.get(light) + delta));
                boolean types = Objects.equals(a.taskType(), b.taskType())
                        || (sameType(light, a.taskType()) < SAME_TYPE_PER_DAY
                                && sameType(heavy, b.taskType()) < SAME_TYPE_PER_DAY);
                if (after < bestGap - 1e-9 && types && load.get(light) + delta <= cap + 1e-9) {
                    bestGap = after;
                    fromHeavy = a;
                    fromLight = b;
                }
            }
        }
        if (fromHeavy == null || fromLight == null) {
            return false;
        }
        move(fromHeavy, heavy, light);
        move(fromLight, light, heavy);
        return true;
    }

    private static boolean balanceable(WeekTask task, List<WeekTask> movable) {
        return task.kind() != WeekTaskKind.MOCK_EXAM
                && task.kind() != WeekTaskKind.VOCABULARY_REVIEW
                && movable.contains(task);
    }

    private void move(WeekTask task, int from, int to) {
        byDay.get(from).remove(task);
        load.put(from, load.get(from) - task.estimatedMinutes());
        put(task, to);
    }

    private void put(WeekTask task, int day) {
        task.moveTo(day);
        byDay.get(day).add(task);
        load.put(day, load.get(day) + task.estimatedMinutes());
    }

    private int sameType(int day, TaskType type) {
        if (type == null || !byDay.containsKey(day)) {
            return 0;
        }
        int count = 0;
        for (WeekTask other : byDay.get(day)) {
            if (other.taskType() == type) {
                count++;
            }
        }
        return count;
    }

    private int sameSection(int day, WeekSection section) {
        int count = 0;
        for (WeekTask other : byDay.get(day)) {
            if (other.section() == section && other.kind() != WeekTaskKind.VOCABULARY_REVIEW) {
                count++;
            }
        }
        return count;
    }

    /** Order inside a day (§10.3 step 5); the sort order of the week follows the days. */
    public static void order(List<WeekTask> tasks) {
        List<WeekTask> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingInt(WeekTask::dayNo)
                .thenComparingInt(DayDistributor::rank)
                .thenComparing(
                        Comparator.comparingDouble(WeekTask::estimatedMinutes).reversed())
                .thenComparing(WeekTask::title));
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).order(i + 1);
        }
    }

    private static int rank(WeekTask task) {
        if (task.kind() == WeekTaskKind.VOCABULARY_REVIEW) {
            return 0;
        }
        if (task.kind() == WeekTaskKind.MOCK_EXAM) {
            return 1;
        }
        return switch (task.section()) {
            case WRITING, SPEAKING -> 2;
            case READING, LISTENING -> 3;
            case GRAMMAR, VOCABULARY -> 4;
            case RESOURCES -> 5;
        };
    }
}
