package com.toefly.vocabulary.domain.fsrs;

/** FSRS grade G: 1 Again, 2 Hard, 3 Good, 4 Easy. */
public enum Rating {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int grade;

    Rating(int grade) {
        this.grade = grade;
    }

    public int grade() {
        return grade;
    }

    public static Rating ofGrade(int grade) {
        for (Rating rating : values()) {
            if (rating.grade == grade) {
                return rating;
            }
        }
        throw new IllegalArgumentException("rating must be 1..4: " + grade);
    }
}
