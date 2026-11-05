package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemType;

/** Goal and onboarding exist only for learners (INV-PRF-04). */
@ProblemType(value = "forbidden", status = 403)
public class StudentOnlyException extends RuntimeException {

    public StudentOnlyException() {
        super("Only learners have a study goal and an onboarding.");
    }
}
