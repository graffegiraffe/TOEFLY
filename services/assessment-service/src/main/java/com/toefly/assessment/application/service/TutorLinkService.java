package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.TutorLinkUseCase;
import com.toefly.assessment.application.port.out.TutorLinkRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Keeps {@code tutor_student_link} in step with classroom events. */
@Service
class TutorLinkService implements TutorLinkUseCase {

    private final TutorLinkRepository links;

    TutorLinkService(TutorLinkRepository links) {
        this.links = links;
    }

    @Override
    public void enrolled(UUID tutorId, UUID studentId) {
        links.link(tutorId, studentId);
    }

    @Override
    public void unenrolled(UUID tutorId, UUID studentId) {
        links.unlink(tutorId, studentId);
    }
}
