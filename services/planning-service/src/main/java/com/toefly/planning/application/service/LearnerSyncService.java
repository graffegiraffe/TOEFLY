package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.SyncLearnerUseCase;
import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.ItemSetCatalogPort.CatalogEntry;
import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LearnerSyncService implements SyncLearnerUseCase {

    private final LearnerSignalPort signals;
    private final ItemSetCatalogPort catalog;
    private final StudyPlanRepository plans;
    private final WeekRepository weeks;

    public LearnerSyncService(
            LearnerSignalPort signals, ItemSetCatalogPort catalog, StudyPlanRepository plans, WeekRepository weeks) {
        this.signals = signals;
        this.catalog = catalog;
        this.plans = plans;
        this.weeks = weeks;
    }

    @Override
    public void abilityUpdated(UUID userId, WeekSection section, double theta, Instant occurredAt) {
        signals.recordTheta(userId, section, theta, occurredAt);
    }

    @Override
    public void itemSetPublished(
            UUID itemSetId,
            int version,
            TaskType taskType,
            String examFormat,
            double difficultyB,
            double estimatedMinutes,
            String topic,
            @Nullable UUID ownerTutorId,
            Instant occurredAt) {
        catalog.published(
                new CatalogEntry(
                        itemSetId, version, taskType, examFormat, difficultyB, estimatedMinutes, topic, ownerTutorId),
                occurredAt);
    }

    @Override
    public void itemSetHidden(UUID itemSetId, Instant occurredAt) {
        catalog.hidden(itemSetId, occurredAt);
    }

    @Override
    public void deleteUser(UUID userId) {
        weeks.deleteByUser(userId);
        plans.deleteByUser(userId);
        signals.deleteByUser(userId);
    }
}
