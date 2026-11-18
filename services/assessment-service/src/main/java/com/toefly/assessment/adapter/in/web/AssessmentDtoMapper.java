package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.dto.CriterionScore;
import com.toefly.assessment.adapter.in.web.dto.DeferredInfo;
import com.toefly.assessment.adapter.in.web.dto.OverallEstimate;
import com.toefly.assessment.adapter.in.web.dto.SubmissionSummary;
import com.toefly.assessment.application.port.in.SubmissionView;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.BandScale;
import com.toefly.assessment.domain.estimate.SectionEstimate;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Domain objects to the DTOs of assessment-service.yaml. */
final class AssessmentDtoMapper {

    private AssessmentDtoMapper() {}

    static com.toefly.assessment.adapter.in.web.dto.Submission submission(SubmissionView view, Instant now) {
        Submission s = view.submission();
        List<com.toefly.assessment.adapter.in.web.dto.SubmissionVersion> versions = new ArrayList<>();
        Double previous = null;
        for (SubmissionVersion v : s.versions()) {
            var dto = version(v, view.evaluations(), previous);
            versions.add(dto);
            if (dto.getEffectiveScore() != null) {
                previous = dto.getEffectiveScore().doubleValue();
            }
        }
        return new com.toefly.assessment.adapter.in.web.dto.Submission()
                .id(s.id())
                .userId(s.userId())
                .itemSetId(s.itemSetId())
                .itemSetVersion(s.itemSetVersion())
                .taskType(com.toefly.assessment.adapter.in.web.dto.TaskType.fromValue(
                        s.taskType().name()))
                .mode(com.toefly.assessment.adapter.in.web.dto.AttemptMode.fromValue(
                        s.mode().name()))
                .weekTaskId(s.weekTaskId())
                .examSessionId(s.examSessionId())
                .status(status(s.status()))
                .currentVersionNo(s.current().versionNo())
                .version(s.version())
                .createdAt(time(s.createdAt()))
                .timeLimitSeconds(s.taskType().timeLimitSeconds())
                .versions(versions)
                .deferred(deferred(view.deferred(), now));
    }

    static com.toefly.assessment.adapter.in.web.dto.SubmissionVersion version(
            SubmissionVersion v, List<Evaluation> evaluations, @Nullable Double previousScore) {
        Evaluation ai = find(evaluations, v.versionNo(), EvaluatorType.AI);
        Evaluation tutor = find(evaluations, v.versionNo(), EvaluatorType.TUTOR);
        Evaluation effective = tutor != null ? tutor : ai;
        Double score = effective == null ? null : effective.finalScore();
        return new com.toefly.assessment.adapter.in.web.dto.SubmissionVersion()
                .versionNo(v.versionNo())
                .status(status(v.status()))
                .text(v.text())
                .wordCount(v.wordCount())
                .activeSeconds(v.activeSeconds())
                .overtimeSeconds(v.overtimeSeconds())
                .submittedAt(v.submittedAt() == null ? null : time(v.submittedAt()))
                .countsForEstimate(v.countsForEstimate())
                .evaluation(ai == null ? null : evaluation(ai))
                .tutorEvaluation(tutor == null ? null : evaluation(tutor))
                .effectiveScore(decimal(score))
                .scoreDelta(score == null || previousScore == null ? null : decimal(score - previousScore));
    }

    static com.toefly.assessment.adapter.in.web.dto.Evaluation evaluation(Evaluation e) {
        return new com.toefly.assessment.adapter.in.web.dto.Evaluation()
                .id(e.id())
                .versionNo(e.versionNo())
                .evaluator(com.toefly.assessment.adapter.in.web.dto.EvaluatorType.fromValue(
                        e.evaluator().name()))
                .tutorId(e.tutorId())
                .rubricId(e.rubricId())
                .finalScore(BigDecimal.valueOf(e.finalScore()))
                .overallScore(e.overallScore())
                .criteria(e.criteria().stream()
                        .map(c -> new CriterionScore()
                                .criterion(c.criterion())
                                .level(c.level())
                                .note(c.note()))
                        .toList())
                .strengths(e.strengths())
                .priorities(e.priorities())
                .annotations(e.annotations().stream()
                        .map(AssessmentDtoMapper::annotation)
                        .toList())
                .agreement(
                        e.agreement() == null
                                ? null
                                : com.toefly.assessment.adapter.in.web.dto.EvaluationAgreement.fromValue(
                                        e.agreement().name()))
                .needsHumanReview(e.needsHumanReview())
                .capsApplied(e.capsApplied())
                .integrityFlags(e.integrityFlags())
                .degraded(e.degraded())
                .createdAt(time(e.createdAt()));
    }

    static com.toefly.assessment.adapter.in.web.dto.InlineAnnotation annotation(InlineAnnotation a) {
        return new com.toefly.assessment.adapter.in.web.dto.InlineAnnotation()
                .id(a.id())
                .start(a.start())
                .end(a.end())
                .anchorQuote(a.anchorQuote())
                .category(com.toefly.assessment.adapter.in.web.dto.MistakeCategory.fromValue(
                        a.category().name()))
                .subcategory(a.subcategory())
                .severity(com.toefly.assessment.adapter.in.web.dto.Severity.fromValue(
                        a.severity().name()))
                .message(a.message())
                .suggestion(a.suggestion())
                .grammarTopic(a.grammarTopic())
                .unitTitle(a.unitTitle())
                .status(com.toefly.assessment.adapter.in.web.dto.AnnotationStatus.fromValue(
                        a.status().name()));
    }

    static SubmissionSummary summary(Submission s) {
        return new SubmissionSummary()
                .id(s.id())
                .itemSetId(s.itemSetId())
                .taskType(com.toefly.assessment.adapter.in.web.dto.TaskType.fromValue(
                        s.taskType().name()))
                .mode(com.toefly.assessment.adapter.in.web.dto.AttemptMode.fromValue(
                        s.mode().name()))
                .weekTaskId(s.weekTaskId())
                .status(status(s.status()))
                .currentVersionNo(s.current().versionNo())
                .latestScore(null)
                .createdAt(time(s.createdAt()));
    }

    static com.toefly.assessment.adapter.in.web.dto.BandEstimate estimate(BandEstimate estimate) {
        List<com.toefly.assessment.adapter.in.web.dto.Section> missing = new ArrayList<>();
        List<com.toefly.assessment.adapter.in.web.dto.SectionEstimate> sections = new ArrayList<>();
        for (SectionEstimate s : estimate.sections()) {
            var section = com.toefly.assessment.adapter.in.web.dto.Section.fromValue(
                    s.section().name());
            var dto = new com.toefly.assessment.adapter.in.web.dto.SectionEstimate()
                    .section(section)
                    .status(
                            s.ready()
                                    ? com.toefly.assessment.adapter.in.web.dto.SectionEstimate.StatusEnum.READY
                                    : com.toefly.assessment.adapter.in.web.dto.SectionEstimate.StatusEnum
                                            .NEED_MORE_DATA)
                    .n(s.n())
                    .remaining(s.remaining());
            if (s.ready() && s.low() != null && s.high() != null) {
                dto.pointBand(decimal(s.point()))
                        .low(decimal(s.low()))
                        .high(decimal(s.high()))
                        .cefrLow(BandScale.cefr(s.low()))
                        .cefrHigh(BandScale.cefr(s.high()))
                        .comparableLow(BandScale.comparableLow(s.low()))
                        .comparableHigh(BandScale.comparableHigh(s.high()))
                        .confidence(confidence(s.high() - s.low()));
            } else {
                missing.add(section);
            }
            sections.add(dto);
        }
        BandEstimate.Overall overall = estimate.overall();
        return new com.toefly.assessment.adapter.in.web.dto.BandEstimate()
                .sections(sections)
                .needsMoreData(missing)
                .asOf(time(estimate.asOf()))
                .overall(
                        overall == null
                                ? null
                                : new OverallEstimate()
                                        .pointBand(decimal(overall.point()))
                                        .low(decimal(overall.low()))
                                        .high(decimal(overall.high()))
                                        .cefr(BandScale.cefr(overall.point()))
                                        .cefrLow(BandScale.cefr(overall.low()))
                                        .cefrHigh(BandScale.cefr(overall.high()))
                                        .comparableLow(BandScale.comparableLow(overall.low()))
                                        .comparableHigh(BandScale.comparableHigh(overall.high())));
    }

    static com.toefly.assessment.adapter.in.web.dto.DeferredEvaluation deferredRow(DeferredEvaluation d) {
        return new com.toefly.assessment.adapter.in.web.dto.DeferredEvaluation()
                .submissionId(d.submissionId())
                .versionNo(d.versionNo())
                .userId(d.userId())
                .attempts(d.attempts())
                .deferredAt(time(d.deferredAt()))
                .nextAttemptAt(time(d.nextAttemptAt()))
                .lastError(d.lastError())
                .deadLettered(d.deadLettered());
    }

    private static com.toefly.assessment.adapter.in.web.dto.SectionEstimate.ConfidenceEnum confidence(double width) {
        if (width <= 0.5) {
            return com.toefly.assessment.adapter.in.web.dto.SectionEstimate.ConfidenceEnum.HIGH;
        }
        return width <= 1.0
                ? com.toefly.assessment.adapter.in.web.dto.SectionEstimate.ConfidenceEnum.MEDIUM
                : com.toefly.assessment.adapter.in.web.dto.SectionEstimate.ConfidenceEnum.LOW;
    }

    private static @Nullable DeferredInfo deferred(@Nullable DeferredEvaluation d, Instant now) {
        if (d == null) {
            return null;
        }
        long seconds = Math.max(0, Duration.between(now, d.nextAttemptAt()).toSeconds());
        return new DeferredInfo()
                .attempts(d.attempts())
                .nextAttemptAt(time(d.nextAttemptAt()))
                .retryAfterSeconds((int) seconds);
    }

    private static @Nullable Evaluation find(List<Evaluation> evaluations, int versionNo, EvaluatorType who) {
        return evaluations.stream()
                .filter(e -> e.versionNo() == versionNo && e.evaluator() == who)
                .findFirst()
                .orElse(null);
    }

    static com.toefly.assessment.adapter.in.web.dto.SubmissionStatus status(
            com.toefly.assessment.domain.submission.SubmissionStatus status) {
        return com.toefly.assessment.adapter.in.web.dto.SubmissionStatus.fromValue(status.name());
    }

    private static @Nullable BigDecimal decimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
