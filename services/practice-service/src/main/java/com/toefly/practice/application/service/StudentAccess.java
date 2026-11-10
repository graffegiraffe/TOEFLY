package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AccessDeniedToStudentException;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.out.TutorLinkPort;
import com.toefly.practice.domain.attempt.AttemptNotFoundException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Whose data a caller may read (docs/05-api-contracts.md §2.8): a learner reads their own, a tutor reads linked
 * students ({@code x-toefly-tutor-link}); foreign attempts look missing to a learner.
 */
@Component
class StudentAccess {

    private final TutorLinkPort links;

    StudentAccess(TutorLinkPort links) {
        this.links = links;
    }

    /** The user whose data the request is about. */
    UUID target(Caller caller, @Nullable UUID studentId) {
        if (studentId == null || studentId.equals(caller.userId())) {
            return caller.userId();
        }
        if (caller.is(Caller.Role.TUTOR) && links.linked(caller.userId(), studentId)) {
            return studentId;
        }
        throw new AccessDeniedToStudentException();
    }

    /** Checks that the caller may read an attempt of {@code owner}. */
    void requireReadable(Caller caller, UUID owner, UUID attemptId) {
        if (owner.equals(caller.userId())) {
            return;
        }
        if (caller.is(Caller.Role.TUTOR) && links.linked(caller.userId(), owner)) {
            return;
        }
        if (caller.is(Caller.Role.TUTOR)) {
            throw new AccessDeniedToStudentException();
        }
        throw new AttemptNotFoundException(attemptId);
    }
}
