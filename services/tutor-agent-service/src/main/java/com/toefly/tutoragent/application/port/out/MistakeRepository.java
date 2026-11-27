package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.Opportunity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Mistake records and opportunities of learners. */
public interface MistakeRepository {

    void append(UUID userId, Collection<MistakeRecord> records, Collection<Opportunity> opportunities);

    List<MistakeRecord> records(UUID userId, Instant since);

    List<Opportunity> opportunities(UUID userId, Instant since);

    /** Records of one subcategory, newest first, with text (examples of reviews and tools). */
    List<MistakeRecord> examples(UUID userId, String subcategory, Instant since, int limit);
}
