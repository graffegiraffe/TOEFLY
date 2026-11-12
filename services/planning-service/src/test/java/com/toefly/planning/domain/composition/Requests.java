package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.WeekSection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class Requests {

    /** Closed types of this release: the open Writing and Speaking types wait for phases 5b and 6. */
    static final Set<TaskType> CLOSED = EnumSet.of(
            TaskType.COMPLETE_THE_WORDS,
            TaskType.READ_DAILY_LIFE,
            TaskType.READ_ACADEMIC_PASSAGE,
            TaskType.LISTEN_CHOOSE_RESPONSE,
            TaskType.LISTEN_CONVERSATION,
            TaskType.LISTEN_ANNOUNCEMENT,
            TaskType.LISTEN_ACADEMIC_TALK,
            TaskType.BUILD_A_SENTENCE,
            TaskType.GRAMMAR_EXERCISE);

    /** Every item-set type of the exam and the grammar exercises: the full composition of later releases. */
    static final Set<TaskType> ALL = EnumSet.copyOf(TaskType.EXAM);

    static {
        ALL.add(TaskType.GRAMMAR_EXERCISE);
    }

    static final List<ResourceLink> RESOURCES = List.of(
            new ResourceLink("https://www.toeflresources.com/reading-section/", "Reading", Set.of(WeekSection.READING)),
            new ResourceLink(
                    "https://www.toeflresources.com/listening-section/", "Listening", Set.of(WeekSection.LISTENING)),
            new ResourceLink(
                    "https://www.toeflresources.com/writing-section/", "Writing", Set.of(WeekSection.WRITING)));

    static CompositionRequest request(int weekNo, int minutes, List<TaskType> weak, Set<TaskType> types, boolean mock) {
        return new CompositionRequest(
                weekNo,
                1,
                minutes,
                weak,
                types,
                false,
                mock,
                Optional.of(new UnitRef(UUID.randomUUID(), "Round-Up Unit " + weekNo)),
                Optional.of(new UnitRef(UUID.randomUUID(), "Collins Grammar Unit " + weekNo)),
                Optional.of(new UnitRef(UUID.randomUUID(), "Collins Vocabulary Unit " + weekNo)),
                RESOURCES,
                Map.of(),
                new FakeSupply(200, false));
    }

    private Requests() {}
}
