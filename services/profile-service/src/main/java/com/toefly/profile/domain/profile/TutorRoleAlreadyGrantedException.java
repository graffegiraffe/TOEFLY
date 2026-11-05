package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemType;

/** The user already has the TUTOR role (docs/05-api-contracts.md §6.1, POST /me/tutor-role). */
@ProblemType(value = "conflict", status = 409)
public class TutorRoleAlreadyGrantedException extends RuntimeException {

    public TutorRoleAlreadyGrantedException() {
        super("You already have a tutor workspace.");
    }
}
