package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.adapter.in.web.dto.ActivityDay;
import com.toefly.analytics.adapter.in.web.dto.AiBreakdown;
import com.toefly.analytics.adapter.in.web.dto.AiDailyCost;
import com.toefly.analytics.adapter.in.web.dto.AiTotals;
import com.toefly.analytics.adapter.in.web.dto.AiUsageReport;
import com.toefly.analytics.adapter.in.web.dto.BandEstimateView;
import com.toefly.analytics.adapter.in.web.dto.BandRange;
import com.toefly.analytics.adapter.in.web.dto.CurrentWeek;
import com.toefly.analytics.adapter.in.web.dto.DayTime;
import com.toefly.analytics.adapter.in.web.dto.Funnel;
import com.toefly.analytics.adapter.in.web.dto.PaceVerdict;
import com.toefly.analytics.adapter.in.web.dto.ProductDay;
import com.toefly.analytics.adapter.in.web.dto.ProductMetrics;
import com.toefly.analytics.adapter.in.web.dto.ProjectionName;
import com.toefly.analytics.adapter.in.web.dto.ProjectionRebuildJob;
import com.toefly.analytics.adapter.in.web.dto.RecentResult;
import com.toefly.analytics.adapter.in.web.dto.Retention;
import com.toefly.analytics.adapter.in.web.dto.Section;
import com.toefly.analytics.adapter.in.web.dto.SectionBandPoint;
import com.toefly.analytics.adapter.in.web.dto.SectionEstimate;
import com.toefly.analytics.adapter.in.web.dto.SectionTime;
import com.toefly.analytics.adapter.in.web.dto.Streak;
import com.toefly.analytics.adapter.in.web.dto.StreakDay;
import com.toefly.analytics.adapter.in.web.dto.StreakSummary;
import com.toefly.analytics.adapter.in.web.dto.StudentDashboard;
import com.toefly.analytics.adapter.in.web.dto.Targets;
import com.toefly.analytics.adapter.in.web.dto.TaskType;
import com.toefly.analytics.adapter.in.web.dto.TaskTypeTime;
import com.toefly.analytics.adapter.in.web.dto.ThetaEntry;
import com.toefly.analytics.adapter.in.web.dto.TimeReport;
import com.toefly.analytics.adapter.in.web.dto.TodayGoal;
import com.toefly.analytics.adapter.in.web.dto.WeekPoint;
import com.toefly.analytics.adapter.in.web.dto.WeekSection;
import com.toefly.analytics.application.port.in.RebuildJob;
import com.toefly.analytics.application.port.in.StreakResult;
import com.toefly.analytics.application.port.in.StudentDashboardResult;
import com.toefly.analytics.domain.activity.PaceTally;
import com.toefly.analytics.domain.dashboard.BandSnapshot;
import com.toefly.analytics.domain.dashboard.SectionBand;
import com.toefly.analytics.domain.dashboard.WeeklyProgress;
import com.toefly.analytics.domain.learner.LearnerSettings;
import com.toefly.analytics.domain.metrics.AiUsageReport.Breakdown;
import com.toefly.analytics.domain.metrics.AiUsageReport.Totals;
import com.toefly.analytics.domain.metrics.ProductReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Read models to the DTOs of analytics-service.yaml. */
final class AnalyticsDtoMapper {

    private AnalyticsDtoMapper() {}

    static StudentDashboard dashboard(StudentDashboardResult r) {
        var d = r.dashboard();
        var week = d.currentWeek();
        var today = r.todayActivity();
        LearnerSettings settings = r.settings();
        return new StudentDashboard()
                .userId(d.userId())
                .asOf(time(r.asOf()))
                .today(r.today())
                .timezone(settings.timezone().getId())
                .currentWeek(
                        week == null
                                ? null
                                : new CurrentWeek()
                                        .weekId(week.weekId())
                                        .weekNo(week.weekNo())
                                        .day1(week.day1())
                                        .day(week.completed() ? null : week.dayOfWeek(r.today()))
                                        .progressPercent(dec(week.progressPercent()))
                                        .tasksTotal(week.tasksTotal())
                                        .completed(week.completed()))
                .bandEstimate(band(d.band()))
                .theta(d.theta().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new ThetaEntry()
                                .section(Section.valueOf(e.getKey().name()))
                                .theta(dec(e.getValue().value()))
                                .standardError(dec(e.getValue().standardError()))
                                .asOf(time(e.getValue().asOf())))
                        .toList())
                .lastResults(d.lastResults().stream()
                        .map(result -> new RecentResult()
                                .id(result.id())
                                .kind(RecentResult.KindEnum.valueOf(
                                        result.kind().name()))
                                .taskType(TaskType.valueOf(result.taskType().name()))
                                .section(
                                        result.section() == null
                                                ? null
                                                : Section.valueOf(
                                                        result.section().name()))
                                .correctCount(result.correct())
                                .totalCount(result.total())
                                .rubricScore(result.rubricScore())
                                .at(time(result.at())))
                        .toList())
                .examDate(settings.examDate())
                .examCountdownDays(r.examCountdownDays())
                .targets(new Targets()
                        .overall(nullableDec(settings.overallTarget()))
                        .reading(nullableDec(
                                settings.sectionTargets().get(com.toefly.analytics.domain.common.Section.READING)))
                        .listening(nullableDec(
                                settings.sectionTargets().get(com.toefly.analytics.domain.common.Section.LISTENING)))
                        .writing(nullableDec(
                                settings.sectionTargets().get(com.toefly.analytics.domain.common.Section.WRITING)))
                        .speaking(nullableDec(
                                settings.sectionTargets().get(com.toefly.analytics.domain.common.Section.SPEAKING))))
                .todayGoal(new TodayGoal()
                        .date(r.today())
                        .activeSeconds(today == null ? 0 : today.activeSeconds())
                        .goalMinutes(today == null ? settings.minutesPerDay() : today.goalMinutes())
                        .goalProgress(dec(today == null ? 0 : today.goalProgress()))
                        .goalMet(today != null && today.goalMet())
                        .tasksDone(today == null ? 0 : today.tasksDone()))
                .streak(new StreakSummary()
                        .current(r.streak().current())
                        .longest(r.streak().longest())
                        .todayDone(r.streak().todayDone()))
                .weekActiveSeconds(r.weekActiveSeconds())
                .weeks(r.weeks().stream().map(AnalyticsDtoMapper::week).toList());
    }

    static Streak streak(StreakResult r) {
        return new Streak()
                .current(r.streak().current())
                .longest(r.streak().longest())
                .todayDone(r.streak().todayDone())
                .goalMinutes(r.streak().goalMinutes())
                .today(r.today())
                .timezone(r.timezone())
                .todayActiveSeconds(r.todayActiveSeconds())
                .todayGoalProgress(dec(r.todayGoalProgress()))
                .days(r.streak().lastDays().stream()
                        .map(day -> new StreakDay().date(day.date()).done(day.done()))
                        .toList())
                .asOf(time(r.asOf()));
    }

    static ActivityDay activityDay(com.toefly.analytics.domain.activity.ActivityDay day) {
        return new ActivityDay()
                .date(day.date())
                .activeSeconds(day.activeSeconds())
                .bySection(sections(day.secondsBySection()))
                .byTaskType(day.secondsByTaskType().entrySet().stream()
                        .map(e ->
                                taskType(e.getKey(), e.getValue(), day.pace().getOrDefault(e.getKey(), PaceTally.NONE)))
                        .toList())
                .tasksDone(day.tasksDone())
                .goalMinutes(day.goalMinutes())
                .goalMet(day.goalMet());
    }

    static TimeReport timeReport(com.toefly.analytics.domain.activity.TimeReport report) {
        return new TimeReport()
                .from(report.from())
                .to(report.to())
                .totalSeconds(report.totalSeconds())
                .plannedSeconds(report.plannedSeconds())
                .bySection(sections(report.bySection()))
                .byTaskType(report.byTaskType().stream()
                        .map(t -> taskType(t.taskType(), t.activeSeconds(), t.pace()))
                        .toList())
                .byDay(report.byDay().stream()
                        .map(day -> new DayTime()
                                .date(day.date())
                                .activeSeconds(day.activeSeconds())
                                .bySection(sections(day.bySection()))
                                .goalMet(day.goalMet()))
                        .toList());
    }

    static ProductMetrics product(ProductReport report) {
        ProductReport.Funnel funnel = report.funnel();
        return new ProductMetrics()
                .from(report.from())
                .to(report.to())
                .funnel(new Funnel()
                        .registered(funnel.registered())
                        .onboarded(funnel.onboarded())
                        .planCreated(funnel.planCreated())
                        .firstTaskDone(funnel.firstTaskDone())
                        .activated(funnel.activated()))
                .retention(report.retention().stream()
                        .map(r -> new Retention()
                                .name(r.name())
                                .startDay(r.startDay())
                                .endDay(r.endDay())
                                .eligible(r.eligible())
                                .retained(r.retained())
                                .rate(nullableDec(r.rate())))
                        .toList())
                .weekCompletionRate(nullableDec(report.weekCompletionRate()))
                .medianStudyDaysRatio(nullableDec(report.medianStudyDaysRatio()))
                .learnersInSdr(report.learnersInSdr())
                .daily(report.daily().stream()
                        .map(c -> new ProductDay()
                                .day(c.day())
                                .newUsers(c.newUsers())
                                .onboarded(c.onboarded())
                                .plansCreated(c.plansCreated())
                                .firstTasks(c.firstTasks())
                                .attemptsSubmitted(c.attemptsSubmitted())
                                .weeksCompleted(c.weeksCompleted())
                                .weeksCompletedByProgress(c.weeksCompletedByProgress()))
                        .toList());
    }

    static AiUsageReport aiUsage(com.toefly.analytics.domain.metrics.AiUsageReport report) {
        return new AiUsageReport()
                .from(report.from())
                .to(report.to())
                .totals(totals(report.totals()))
                .byProvider(breakdown(report.byProvider()))
                .byModel(breakdown(report.byModel()))
                .byPrompt(breakdown(report.byPrompt()))
                .byDay(report.byDay().stream()
                        .map(d ->
                                new AiDailyCost().day(d.day()).calls(d.calls()).costMicroUsd(d.costMicroUsd()))
                        .toList())
                .activeLearners(report.activeLearners())
                .costPerActiveLearnerMicroUsd(report.costPerActiveLearnerMicroUsd());
    }

    static ProjectionRebuildJob job(RebuildJob job) {
        return new ProjectionRebuildJob()
                .id(job.id())
                .projection(ProjectionName.fromValue(job.projection().path()))
                .status(ProjectionRebuildJob.StatusEnum.valueOf(job.status().name()))
                .requestedAt(time(job.requestedAt()))
                .finishedAt(time(job.finishedAt()))
                .eventsReplayed(job.eventsReplayed())
                .error(job.error());
    }

    private static @Nullable BandEstimateView band(@Nullable BandSnapshot band) {
        if (band == null) {
            return null;
        }
        return new BandEstimateView()
                .overall(range(band.overallPoint(), band.overallLow(), band.overallHigh()))
                .sections(band.sections().stream()
                        .sorted(Comparator.comparing(SectionBand::section))
                        .map(s -> new SectionEstimate()
                                .section(Section.valueOf(s.section().name()))
                                .status(
                                        s.ready()
                                                ? SectionEstimate.StatusEnum.READY
                                                : SectionEstimate.StatusEnum.NEED_MORE_DATA)
                                .point(nullableDec(s.point()))
                                .low(nullableDec(s.low()))
                                .high(nullableDec(s.high()))
                                .n(s.n()))
                        .toList())
                .asOf(time(band.asOf()));
    }

    private static WeekPoint week(WeeklyProgress w) {
        return new WeekPoint()
                .weekNo(w.weekNo())
                .day1(w.day1())
                .progressPercent(dec(w.progressPercent()))
                .tasksDone(w.tasksDone())
                .tasksTotal(w.tasksTotal())
                .completed(w.completed())
                .overall(range(w.overallPoint(), w.overallLow(), w.overallHigh()))
                .sections(w.sections().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new SectionBandPoint()
                                .section(Section.valueOf(e.getKey().name()))
                                .point(dec(e.getValue())))
                        .toList());
    }

    private static @Nullable BandRange range(@Nullable Double point, @Nullable Double low, @Nullable Double high) {
        if (point == null || low == null || high == null) {
            return null;
        }
        return new BandRange().point(dec(point)).low(dec(low)).high(dec(high));
    }

    private static List<SectionTime> sections(Map<com.toefly.analytics.domain.common.WeekSection, Integer> seconds) {
        return seconds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SectionTime()
                        .section(WeekSection.valueOf(e.getKey().name()))
                        .activeSeconds(e.getValue()))
                .toList();
    }

    private static TaskTypeTime taskType(
            com.toefly.analytics.domain.common.TaskType type, int seconds, PaceTally pace) {
        return new TaskTypeTime()
                .taskType(TaskType.valueOf(type.name()))
                .activeSeconds(seconds)
                .normSeconds(pace.normSeconds())
                .paceRatio(nullableDec(pace.ratio()))
                .verdict(PaceVerdict.valueOf(pace.verdict().name()))
                .attempts(pace.attempts())
                .slowAttempts(pace.slowAttempts())
                .fastAttempts(pace.fastAttempts());
    }

    private static List<AiBreakdown> breakdown(List<Breakdown> rows) {
        return rows.stream()
                .map(b -> new AiBreakdown().key(b.key()).totals(totals(b.totals())))
                .toList();
    }

    private static AiTotals totals(Totals t) {
        return new AiTotals()
                .calls(t.calls())
                .tokensIn(t.tokensIn())
                .tokensOut(t.tokensOut())
                .costMicroUsd(t.costMicroUsd())
                .fallbackCalls(t.fallbackCalls())
                .cacheHits(t.cacheHits())
                .fallbackShare(nullableDec(t.fallbackShare()));
    }

    private static BigDecimal dec(double value) {
        return BigDecimal.valueOf(value);
    }

    private static @Nullable BigDecimal nullableDec(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static @Nullable OffsetDateTime time(@Nullable Instant at) {
        return at == null ? null : at.atOffset(ZoneOffset.UTC);
    }
}
