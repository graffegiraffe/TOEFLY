package com.toefly.tutoragent.application.port.in;

/** The learner lacks the right the operation needs (403 entitlement-required). */
public class EntitlementRequiredException extends RuntimeException {

    private final String entitlement;

    public EntitlementRequiredException(String entitlement) {
        super("This feature needs the " + entitlement + " right.");
        this.entitlement = entitlement;
    }

    public String entitlement() {
        return entitlement;
    }
}
