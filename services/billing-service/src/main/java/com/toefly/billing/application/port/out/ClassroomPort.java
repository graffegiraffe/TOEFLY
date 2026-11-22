package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.plan.PlanTierCode;
import java.util.List;
import java.util.UUID;

/** classroom-service: saga steps ActivateTutorWorkspace and GrantStudentEntitlements (docs/05 §9.3.6). */
public interface ClassroomPort {

    /** {@code PUT /internal/v1/tutors/{tutorId}/workspace}; idempotent. */
    void putWorkspace(UUID tutorId, boolean active, int seatLimit, PlanTierCode tier);

    /** {@code GET /internal/v1/tutors/{tutorId}/enrollments}: students connected to the tutor. */
    List<UUID> enrollments(UUID tutorId);
}
