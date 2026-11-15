package com.toefly.aigateway.domain.prompt;

/** A command against the registry broke a rule of prompts; {@code code} is the problem slug. */
public class PromptRuleException extends RuntimeException {

    private final String code;

    public PromptRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
