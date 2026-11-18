package com.toefly.assessment.domain.submission;

/** Open task types that make a submission (docs/00-README.md §5.1); Writing only in phase 5b. */
public enum TaskType {
    WRITE_EMAIL("ETS_EMAIL_V1", 7 * 60),
    WRITE_ACADEMIC_DISCUSSION("ETS_DISCUSSION_V1", 10 * 60);

    private final String rubricId;
    private final int timeLimitSeconds;

    TaskType(String rubricId, int timeLimitSeconds) {
        this.rubricId = rubricId;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String rubricId() {
        return rubricId;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public String section() {
        return "WRITING";
    }

    public static boolean supports(String name) {
        for (TaskType type : values()) {
            if (type.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
