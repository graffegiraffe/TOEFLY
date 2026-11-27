package com.toefly.tutoragent.adapter.in.web;

import com.toefly.tutoragent.adapter.in.web.dto.BandSnapshot;
import com.toefly.tutoragent.adapter.in.web.dto.ExplanationLanguage;
import com.toefly.tutoragent.adapter.in.web.dto.FocusItem;
import com.toefly.tutoragent.adapter.in.web.dto.LearnerModel;
import com.toefly.tutoragent.adapter.in.web.dto.MistakeExample;
import com.toefly.tutoragent.adapter.in.web.dto.MistakeStat;
import com.toefly.tutoragent.adapter.in.web.dto.ReviewMistake;
import com.toefly.tutoragent.adapter.in.web.dto.SectionStats;
import com.toefly.tutoragent.adapter.in.web.dto.TaskTypeScore;
import com.toefly.tutoragent.adapter.in.web.dto.UnitRef;
import com.toefly.tutoragent.adapter.in.web.dto.WeekStats;
import com.toefly.tutoragent.adapter.in.web.dto.WeeklyReview;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.WeekFacts;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Domain to DTO of tutor-agent-service.yaml. */
final class TutorAgentDtoMapper {

    private TutorAgentDtoMapper() {}

    static WeeklyReview review(com.toefly.tutoragent.domain.review.WeeklyReview r, Taxonomy taxonomy) {
        WeekFacts f = r.facts();
        return new WeeklyReview()
                .reviewId(r.id())
                .weekId(r.weekId())
                .weekNo(r.weekNo())
                .generatedAt(OffsetDateTime.ofInstant(r.generatedAt(), ZoneOffset.UTC))
                .language(ExplanationLanguage.valueOf(r.language().name()))
                .aiFallback(r.aiFallback())
                .lowActivity(r.lowActivity())
                .headline(r.text().headline())
                .summary(r.text().summary())
                .stats(new WeekStats()
                        .tasksTotal(f.tasksTotal())
                        .tasksDone(f.tasksDone())
                        .progressPercent(f.progressPercent())
                        .activeMinutes(f.activeMinutes())
                        .sections(f.sections().stream()
                                .map(s -> new SectionStats()
                                        .section(s.section())
                                        .tasksTotal(s.tasksTotal())
                                        .tasksDone(s.tasksDone())
                                        .minutesPlanned(s.minutesPlanned())
                                        .minutesDone(s.minutesDone()))
                                .toList()))
                .scores(f.scores().stream()
                        .map(s -> new TaskTypeScore()
                                .taskType(s.taskType())
                                .attempts(s.attempts())
                                .accuracyPercent(s.accuracyPercent())
                                .averageScore(s.averageScore()))
                        .toList())
                .bandSnapshot(bands(f.bands(), f.bandDelta()))
                .topMistakes(r.text().topMistakes().stream()
                        .map(m -> new ReviewMistake()
                                .subcategory(m.subcategory())
                                .category(m.subcategory()
                                        .substring(0, m.subcategory().indexOf('.')))
                                .count(m.count())
                                .pattern(m.pattern())
                                .example(
                                        m.example() == null
                                                ? null
                                                : new MistakeExample()
                                                        .wrong(m.example().wrong())
                                                        .right(
                                                                m.example().right() == null
                                                                        ? ""
                                                                        : m.example()
                                                                                .right()))
                                .advice(m.advice())
                                .unitRefs(units(m.unitRefs(), taxonomy)))
                        .toList())
                .strengths(r.text().strengths())
                .nextWeekFocus(r.text().nextWeekFocus().stream()
                        .map(x -> new FocusItem().subcategory(x.subcategory()).why(x.why()))
                        .toList())
                .motivation(r.text().motivation())
                .tutorNote(r.tutorNote());
    }

    static LearnerModel learnerModel(com.toefly.tutoragent.domain.learner.LearnerModel m, Taxonomy taxonomy) {
        return new LearnerModel()
                .version((int) Math.min(Integer.MAX_VALUE, m.version()))
                .updatedAt(m.updatedAt() == null ? null : OffsetDateTime.ofInstant(m.updatedAt(), ZoneOffset.UTC))
                .language(ExplanationLanguage.valueOf(m.language().name()))
                .bands(bands(m.bands(), null))
                .mistakeStats(m.mistakeStats().stream()
                        .map(s -> new MistakeStat()
                                .subcategory(s.subcategory())
                                .category(s.category().name())
                                .title(taxonomy.title(s.subcategory(), m.language()))
                                .weight(s.weight())
                                .errorRate(s.errorRate())
                                .trend(MistakeStat.TrendEnum.valueOf(s.trend().name()))
                                .status(MistakeStat.StatusEnum.valueOf(
                                        s.status().name()))
                                .occurrences(s.occurrences28Days())
                                .lastSeenAt(OffsetDateTime.ofInstant(s.lastSeenAt(), ZoneOffset.UTC))
                                .unitRefs(units(s.unitRefs(), taxonomy)))
                        .toList());
    }

    private static @Nullable BandSnapshot bands(Map<String, Double> bands, @Nullable Double delta) {
        if (bands.isEmpty()) {
            return null;
        }
        return new BandSnapshot()
                .overall(bands.get("OVERALL"))
                .reading(bands.get("READING"))
                .listening(bands.get("LISTENING"))
                .writing(bands.get("WRITING"))
                .speaking(bands.get("SPEAKING"))
                .overallDelta(delta);
    }

    private static List<UnitRef> units(List<String> codes, Taxonomy taxonomy) {
        return codes.stream()
                .map(code -> new UnitRef().code(code).title(taxonomy.unitTitle(code)))
                .toList();
    }
}
