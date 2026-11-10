package com.toefly.practice.application.port.out;

import java.util.UUID;

/** Local projection tutor_student_link (classroom events, phase 10). */
public interface TutorLinkPort {

    boolean linked(UUID tutorId, UUID studentId);
}
