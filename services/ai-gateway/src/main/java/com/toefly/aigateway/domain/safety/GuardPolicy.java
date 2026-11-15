package com.toefly.aigateway.domain.safety;

/**
 * Where the guard runs and what a violation does (§3.3). The action itself belongs to the caller; the gateway
 * blocks only under {@link #CHAT}.
 */
public enum GuardPolicy {
    CHAT(true, true, true),
    STUDENT_SUBMISSION(true, false, false),
    GENERATED_CONTENT(false, true, false),
    TUTOR_MATERIAL(true, false, false),
    NONE(false, false, false);

    private final boolean input;
    private final boolean output;
    private final boolean blocks;

    GuardPolicy(boolean input, boolean output, boolean blocks) {
        this.input = input;
        this.output = output;
        this.blocks = blocks;
    }

    public boolean checksInput() {
        return input;
    }

    public boolean checksOutput() {
        return output;
    }

    /** A violation refuses the call ({@code ai-guard-blocked}); otherwise the result carries integrity flags. */
    public boolean blocks() {
        return blocks;
    }

    /** CHAT fails closed when the guard model is down; submissions are graded with a flag (§3.4). */
    public boolean failsClosed() {
        return this == CHAT;
    }
}
