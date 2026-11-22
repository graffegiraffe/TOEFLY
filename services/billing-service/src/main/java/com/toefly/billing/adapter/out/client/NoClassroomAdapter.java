package com.toefly.billing.adapter.out.client;

import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.domain.plan.PlanTierCode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Until classroom-service exists (phase 10) a tutor has no workspace to switch and no enrolled students; the saga steps
 * succeed without effect, and classroom will read the seats from subscription-activated when it arrives.
 */
public class NoClassroomAdapter implements ClassroomPort {

    private static final Logger log = LoggerFactory.getLogger(NoClassroomAdapter.class);

    @Override
    public void putWorkspace(UUID tutorId, boolean active, int seatLimit, PlanTierCode tier) {
        log.info(
                "classroom-service is off: workspace of {} would be {} with {} seats",
                tutorId,
                active ? "ACTIVE" : "SUSPENDED",
                seatLimit);
    }

    @Override
    public List<UUID> enrollments(UUID tutorId) {
        return List.of();
    }
}
