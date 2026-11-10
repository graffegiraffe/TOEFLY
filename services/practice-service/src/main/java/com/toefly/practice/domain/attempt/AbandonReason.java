package com.toefly.practice.domain.attempt;

/** Why an attempt ended without scoring (docs/03-domain-model.md §6.5, state diagram). */
public enum AbandonReason {
    USER_EXIT,
    INACTIVITY
}
