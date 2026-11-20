package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.AccessDeniedException;
import com.toefly.analytics.application.port.in.Caller;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Whose data a request reads. A learner reads their own; a tutor reads a student only through an active enrollment
 * (T*, AC-16.03.2), which arrives with classroom-service in phase 10, so until then the access is denied.
 */
final class Access {

    private Access() {}

    static UUID target(Caller caller, @Nullable UUID studentId) {
        if (studentId == null || studentId.equals(caller.userId())) {
            if (!caller.has(Caller.Role.STUDENT)) {
                throw new AccessDeniedException("The dashboard belongs to learners.");
            }
            return caller.userId();
        }
        throw new AccessDeniedException("This learner is not your student.");
    }
}
