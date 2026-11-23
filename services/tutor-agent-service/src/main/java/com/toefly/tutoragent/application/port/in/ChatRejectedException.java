package com.toefly.tutoragent.application.port.in;

/** A chat request refused before any AI call (docs/06 §4.6, ModeGateAdvisor and GuardAdvisor). */
public class ChatRejectedException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        EXAM_IN_PROGRESS,
        MESSAGE_IN_PROGRESS,
        RATE_LIMITED,
        GUARDRAIL_REJECTED,
        ACTION_EXPIRED
    }

    private final Reason reason;

    public ChatRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
