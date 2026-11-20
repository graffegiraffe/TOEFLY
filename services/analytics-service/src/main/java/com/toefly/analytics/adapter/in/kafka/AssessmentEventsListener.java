package com.toefly.analytics.adapter.in.kafka;

import com.toefly.analytics.application.port.in.RecordEventUseCase;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.BandEstimateUpdated;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.in.event.SubmissionEvaluated;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.dashboard.SectionBand;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.assessment.BandEstimateUpdatedV1;
import com.toefly.events.assessment.EstimateStatus;
import com.toefly.events.assessment.SubmissionEvaluatedV1;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** The band estimate and evaluated Writing/Speaking works. */
@Component
public class AssessmentEventsListener {

    private final ProcessedEventGuard guard;
    private final RecordEventUseCase recorder;
    private final ConsistencyLag lag;

    public AssessmentEventsListener(ProcessedEventGuard guard, RecordEventUseCase recorder, ConsistencyLag lag) {
        this.guard = guard;
        this.recorder = recorder;
        this.lag = lag;
    }

    @ToeflyListener(id = "band-estimate-updated", topics = "toefly.assessment.band-estimate-updated.v1")
    void onBand(ConsumerRecord<String, BandEstimateUpdatedV1> record) {
        BandEstimateUpdatedV1 e = record.value();
        if (e == null) {
            return; // tombstone of the compacted topic after an account deletion
        }
        List<SectionBand> sections = e.getSections().stream()
                .map(s -> {
                    Section section = Enums.section(s.getSection());
                    return section == null
                            ? null
                            : new SectionBand(
                                    section,
                                    s.getStatus() == EstimateStatus.READY,
                                    s.getPointBand(),
                                    s.getLow(),
                                    s.getHigh(),
                                    s.getN());
                })
                .filter(Objects::nonNull)
                .toList();
        apply(
                record,
                "AssessmentEventsListener.band",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new BandEstimateUpdated(
                        e.getUserId(),
                        e.getOverallPoint(),
                        e.getOverallLow(),
                        e.getOverallHigh(),
                        sections,
                        e.getOccurredAt()));
    }

    @ToeflyListener(id = "submission-evaluated", topics = "toefly.assessment.submission-evaluated.v1")
    void onEvaluated(ConsumerRecord<String, SubmissionEvaluatedV1> record) {
        SubmissionEvaluatedV1 e = record.value();
        TaskType type = Enums.taskType(e.getTaskType());
        Section section = Enums.section(e.getSection());
        if (type == null || section == null) {
            return;
        }
        apply(
                record,
                "AssessmentEventsListener.evaluated",
                e.getEventId(),
                e.getUserId(),
                e.getOccurredAt(),
                new SubmissionEvaluated(
                        e.getUserId(), e.getSubmissionId(), type, section, e.getRubricScore(), e.getOccurredAt()));
    }

    private void apply(
            ConsumerRecord<?, ?> record, String consumer, UUID eventId, UUID userId, Instant at, AnalyticsEvent event) {
        EventMeta meta = new EventMeta(eventId, record.topic(), at, userId);
        lag.applied(guard.runOnce(record, consumer, () -> recorder.record(meta, event)), at);
    }
}
