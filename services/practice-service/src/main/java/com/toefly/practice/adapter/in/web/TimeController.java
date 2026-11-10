package com.toefly.practice.adapter.in.web;

import com.toefly.practice.adapter.in.web.api.TimeApi;
import com.toefly.practice.adapter.in.web.dto.TimeHeartbeatRequest;
import com.toefly.practice.adapter.in.web.dto.TimeSummary;
import com.toefly.practice.adapter.in.web.dto.TimeSummaryBucketsInner;
import com.toefly.practice.adapter.in.web.dto.TimeTotals;
import com.toefly.practice.application.port.in.GetTimeSummaryUseCase;
import com.toefly.practice.application.port.in.RecordHeartbeatUseCase;
import com.toefly.practice.application.port.in.TimeBucket;
import com.toefly.practice.application.port.in.TimeGrouping;
import com.toefly.practice.domain.attempt.AnswerFormException;
import com.toefly.practice.domain.time.ContextType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Active time outside attempts and summaries against the ETS norms (US-16.01, US-16.02). */
@RestController
public class TimeController implements TimeApi {

    private final RecordHeartbeatUseCase heartbeats;
    private final GetTimeSummaryUseCase summaries;
    private final CurrentCaller caller;

    public TimeController(RecordHeartbeatUseCase heartbeats, GetTimeSummaryUseCase summaries, CurrentCaller caller) {
        this.heartbeats = heartbeats;
        this.summaries = summaries;
        this.caller = caller;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> sendTimeHeartbeat(TimeHeartbeatRequest request) {
        ContextType context = switch (request.getKind()) {
            case GRAMMAR_UNIT -> ContextType.GRAMMAR_STEP;
            case TUTOR_MATERIAL -> ContextType.MATERIAL_VIEW;
            case VOCABULARY_REVIEW, VOCABULARY_UNIT -> ContextType.VOCAB_REVIEW;
            case EXTERNAL_RESOURCE -> ContextType.EXTERNAL_RESOURCE;
            case ITEM_SET, MOCK_EXAM ->
                throw new AnswerFormException("The time of item sets and exams is counted by their attempts.");
        };
        String weekSection = switch (request.getKind()) {
            case GRAMMAR_UNIT -> "GRAMMAR";
            case VOCABULARY_REVIEW, VOCABULARY_UNIT -> "VOCABULARY";
            case TUTOR_MATERIAL, EXTERNAL_RESOURCE -> "RESOURCES";
            default -> null;
        };
        heartbeats.timeHeartbeat(new RecordHeartbeatUseCase.TimeHeartbeat(
                caller.userId(),
                request.getSequence(),
                context,
                request.getWeekTaskId(),
                request.getRefId(),
                weekSection,
                request.getActiveSecondsDelta()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAnyRole('STUDENT', 'TUTOR')")
    public ResponseEntity<TimeSummary> getTimeSummary(
            LocalDate from, LocalDate to, @Nullable UUID studentId, String groupBy) {
        if (!from.isBefore(to)) {
            throw new AnswerFormException("from must be before to.");
        }
        TimeGrouping grouping = TimeGrouping.valueOf(groupBy);
        List<TimeBucket> buckets = summaries.summary(caller.get(), studentId, from, to, grouping);
        return ResponseEntity.ok(new TimeSummary()
                .from(from)
                .to(to)
                .groupBy(TimeSummary.GroupByEnum.fromValue(groupBy))
                .totals(new TimeTotals()
                        .activeSeconds(buckets.stream()
                                .mapToInt(TimeBucket::activeSeconds)
                                .sum())
                        .estimatedSeconds(buckets.stream()
                                .mapToInt(TimeBucket::estimatedSeconds)
                                .sum()))
                .buckets(buckets.stream()
                        .map(bucket -> new TimeSummaryBucketsInner()
                                .key(bucket.key())
                                .activeSeconds(bucket.activeSeconds())
                                .estimatedSeconds(bucket.estimatedSeconds())
                                .attempts(bucket.attempts()))
                        .toList()));
    }
}
