package com.toefly.aigateway.domain.safety;

/**
 * Unmasks a streamed answer chunk by chunk: a placeholder split between chunks ({@code "[PER"} + {@code "SON_1]"})
 * is held back until it is complete.
 */
public final class StreamingUnmasker {

    private static final int LONGEST_PLACEHOLDER = "[DOC_ID_9999]".length();

    private final MaskingSession session;
    private final StringBuilder pending = new StringBuilder();

    public StreamingUnmasker(MaskingSession session) {
        this.session = session;
    }

    /** Text safe to show now; may be empty. */
    public String accept(String chunk) {
        pending.append(chunk);
        int open = pending.lastIndexOf("[");
        int cut = pending.length();
        if (open >= 0 && pending.indexOf("]", open) < 0 && pending.length() - open < LONGEST_PLACEHOLDER) {
            cut = open;
        }
        String ready = pending.substring(0, cut);
        pending.delete(0, cut);
        return session.unmask(ready);
    }

    public String finish() {
        String rest = session.unmask(pending.toString());
        pending.setLength(0);
        return rest;
    }
}
