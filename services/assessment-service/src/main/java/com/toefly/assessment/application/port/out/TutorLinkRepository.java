package com.toefly.assessment.application.port.out;

import java.util.UUID;

/** Tutors and their students, projected from classroom events (filled from phase 10). */
public interface TutorLinkRepository {

    boolean linked(UUID tutorId, UUID studentId);

    void link(UUID tutorId, UUID studentId);

    void unlink(UUID tutorId, UUID studentId);
}
