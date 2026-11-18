package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.out.TutorLinkRepository;
import com.toefly.assessment.domain.submission.SubmissionRuleException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Who may read whose work: the learner, a tutor linked to them, an admin (T* in docs/05-api-contracts.md §6.5). */
@Component
class Access {

    private final TutorLinkRepository links;

    Access(TutorLinkRepository links) {
        this.links = links;
    }

    /** The learner whose data is read: the caller, or {@code studentId} for a linked tutor. */
    UUID student(Caller caller, @Nullable UUID studentId) {
        if (studentId == null || studentId.equals(caller.userId())) {
            return caller.userId();
        }
        if (caller.is(Caller.Role.ADMIN)
                || (caller.is(Caller.Role.TUTOR) && links.linked(caller.userId(), studentId))) {
            return studentId;
        }
        throw new SubmissionRuleException("not-found", "No such student.");
    }

    void read(Caller caller, UUID ownerId) {
        student(caller, ownerId);
    }

    void own(Caller caller, UUID ownerId) {
        if (!caller.userId().equals(ownerId)) {
            throw new SubmissionRuleException("not-found", "No such submission.");
        }
    }

    void tutor(Caller caller, UUID studentId) {
        if (!caller.is(Caller.Role.TUTOR) || !links.linked(caller.userId(), studentId)) {
            throw new SubmissionRuleException("not-found", "No such submission.");
        }
    }
}
