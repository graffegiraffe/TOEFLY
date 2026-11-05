package com.toefly.profile.domain.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Target bands. When targets per section are set, the overall target is their mean rounded to 0.5 (AC-02.01.2,
 * INV-PRF-01); either all four sections are set or none.
 */
public record StudyGoal(
        Band overallTarget,
        @Nullable Band readingTarget,
        @Nullable Band listeningTarget,
        @Nullable Band writingTarget,
        @Nullable Band speakingTarget) {

    public StudyGoal {
        List<Band> sections = sections(readingTarget, listeningTarget, writingTarget, speakingTarget);
        if (!sections.isEmpty() && sections.size() != 4) {
            throw new InvalidProfileValueException("section-targets", "Set a target for every section or for none.");
        }
        if (!sections.isEmpty() && !Band.roundHalfMean(sections).equals(overallTarget)) {
            throw new InvalidProfileValueException(
                    "overall-target", "The overall target is the mean of the section targets rounded to 0.5.");
        }
    }

    public static StudyGoal overall(Band overallTarget) {
        return new StudyGoal(overallTarget, null, null, null, null);
    }

    public static StudyGoal perSection(Band reading, Band listening, Band writing, Band speaking) {
        Band overall = Band.roundHalfMean(List.of(reading, listening, writing, speaking));
        return new StudyGoal(overall, reading, listening, writing, speaking);
    }

    public boolean hasSectionTargets() {
        return readingTarget != null;
    }

    private static List<Band> sections(@Nullable Band... bands) {
        List<Band> present = new ArrayList<>();
        Stream.of(bands).filter(band -> band != null).forEach(present::add);
        return present;
    }
}
