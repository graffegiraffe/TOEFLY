package com.toefly.aigateway.domain.safety;

/** Detector that found a span; on overlap the longer span wins, then the earlier stage (§3.2). */
public enum PiiStage {
    REGEX,
    DICTIONARY,
    NER
}
