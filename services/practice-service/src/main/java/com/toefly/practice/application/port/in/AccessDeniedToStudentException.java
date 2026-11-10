package com.toefly.practice.application.port.in;

import com.toefly.common.web.ProblemType;

/** A tutor asked for a student who is not linked to them (AC-16.03.2). */
@ProblemType(value = "forbidden", status = 403)
public class AccessDeniedToStudentException extends RuntimeException {

    public AccessDeniedToStudentException() {
        super("This learner is not your student.");
    }
}
