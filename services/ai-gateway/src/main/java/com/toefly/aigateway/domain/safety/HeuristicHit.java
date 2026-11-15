package com.toefly.aigateway.domain.safety;

/** Pattern of a prompt injection found without a model (§3.5.2); strong hits alone mean an injection. */
public enum HeuristicHit {
    ROLE_OVERRIDE(true),
    PROMPT_EXTRACTION(true),
    SCORE_MANIPULATION(true),
    DEVELOPER_MODE(true),
    ROLE_TAGS(true),
    ROLE_PLAY(false),
    BASE64_BLOB(false),
    INVISIBLE_CHARS(false);

    private final boolean strong;

    HeuristicHit(boolean strong) {
        this.strong = strong;
    }

    public boolean strong() {
        return strong;
    }
}
