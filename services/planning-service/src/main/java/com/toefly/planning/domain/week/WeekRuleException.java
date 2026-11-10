package com.toefly.planning.domain.week;

/** A command breaks a rule of the week; {@code code} is the problem slug of docs/05-api-contracts.md §3. */
public class WeekRuleException extends RuntimeException {

    private final String code;

    public WeekRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
