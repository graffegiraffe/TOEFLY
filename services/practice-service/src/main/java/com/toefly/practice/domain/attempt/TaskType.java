package com.toefly.practice.domain.attempt;

import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Task types of the canon (docs/00-README.md §5.1). */
public enum TaskType {
    COMPLETE_THE_WORDS,
    READ_DAILY_LIFE,
    READ_ACADEMIC_PASSAGE,
    LISTEN_CHOOSE_RESPONSE,
    LISTEN_CONVERSATION,
    LISTEN_ANNOUNCEMENT,
    LISTEN_ACADEMIC_TALK,
    BUILD_A_SENTENCE,
    WRITE_EMAIL,
    WRITE_ACADEMIC_DISCUSSION,
    LISTEN_AND_REPEAT,
    TAKE_AN_INTERVIEW,
    GRAMMAR_EXERCISE,
    VOCAB_EXERCISE,
    LEGACY_READING,
    LEGACY_LISTENING;

    private static final Set<TaskType> CLOSED = EnumSet.of(
            COMPLETE_THE_WORDS,
            READ_DAILY_LIFE,
            READ_ACADEMIC_PASSAGE,
            LISTEN_CHOOSE_RESPONSE,
            LISTEN_CONVERSATION,
            LISTEN_ANNOUNCEMENT,
            LISTEN_ACADEMIC_TALK,
            BUILD_A_SENTENCE,
            GRAMMAR_EXERCISE,
            VOCAB_EXERCISE,
            LEGACY_READING,
            LEGACY_LISTENING);

    /** Machine-scored types that practice-service checks itself (docs/03-domain-model.md §6.5). */
    public boolean closed() {
        return CLOSED.contains(this);
    }

    /** Items of these types do not share a stimulus, so they count with full weight in theta (§7.4). */
    public boolean independentItems() {
        return this == LISTEN_CHOOSE_RESPONSE || this == BUILD_A_SENTENCE;
    }

    /** Section with its own theta for items of this type; {@code null} when the type does not feed theta (§7.1). */
    public @Nullable Section thetaSection() {
        return switch (this) {
            case COMPLETE_THE_WORDS, READ_DAILY_LIFE, READ_ACADEMIC_PASSAGE -> Section.READING;
            case LISTEN_CHOOSE_RESPONSE, LISTEN_CONVERSATION, LISTEN_ANNOUNCEMENT, LISTEN_ACADEMIC_TALK ->
                Section.LISTENING;
            case BUILD_A_SENTENCE -> Section.WRITING;
            default -> null;
        };
    }

    /** {@code WeekSection} of the type, the bucket of the time tracker (docs/03-domain-model.md §13.3). */
    public String weekSection() {
        return switch (this) {
            case GRAMMAR_EXERCISE -> "GRAMMAR";
            case VOCAB_EXERCISE -> "VOCABULARY";
            case COMPLETE_THE_WORDS, READ_DAILY_LIFE, READ_ACADEMIC_PASSAGE, LEGACY_READING -> "READING";
            case LISTEN_CHOOSE_RESPONSE,
                    LISTEN_CONVERSATION,
                    LISTEN_ANNOUNCEMENT,
                    LISTEN_ACADEMIC_TALK,
                    LEGACY_LISTENING -> "LISTENING";
            case BUILD_A_SENTENCE, WRITE_EMAIL, WRITE_ACADEMIC_DISCUSSION -> "WRITING";
            case LISTEN_AND_REPEAT, TAKE_AN_INTERVIEW -> "SPEAKING";
        };
    }
}
