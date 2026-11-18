package com.toefly.assessment.application.port.in;

import java.util.UUID;

/** Projection of classroom events: which tutor may read and score whose work. */
public interface TutorLinkUseCase {

    void enrolled(UUID tutorId, UUID studentId);

    void unenrolled(UUID tutorId, UUID studentId);
}
