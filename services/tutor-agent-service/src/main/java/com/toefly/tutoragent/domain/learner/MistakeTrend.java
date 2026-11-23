package com.toefly.tutoragent.domain.learner;

/** Error rate of the last 14 days against the 14 days before (docs/06 §6.2). */
public enum MistakeTrend {
    IMPROVING,
    STABLE,
    WORSENING
}
