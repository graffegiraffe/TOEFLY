package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.TaskType;
import java.util.Optional;

/** Hands out item sets of a type one by one, best fit first; never the same set twice in one week. */
public interface SetSupply {

    Optional<ItemSetCandidate> next(TaskType type);

    /** Fresh sets of the type the learner has not been given yet, before this week takes any (docs/03 §10.2). */
    int freshLeft(TaskType type);

    /** Returns a set that the composition dropped, so another slot can take it. */
    void giveBack(ItemSetCandidate candidate);
}
