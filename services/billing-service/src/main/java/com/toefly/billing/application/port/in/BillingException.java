package com.toefly.billing.application.port.in;

/** A refused request with its Problem Details slug (docs/05 §3). */
public class BillingException extends RuntimeException {

    private final Kind kind;
    private final String code;

    public BillingException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static BillingException notFound(String message) {
        return new BillingException(Kind.NOT_FOUND, "not-found", message);
    }

    public static BillingException forbidden(String message) {
        return new BillingException(Kind.FORBIDDEN, "forbidden", message);
    }

    public static BillingException conflict(String code, String message) {
        return new BillingException(Kind.CONFLICT, code, message);
    }

    public static BillingException unprocessable(String code, String message) {
        return new BillingException(Kind.UNPROCESSABLE, code, message);
    }

    public static BillingException invalid(String message) {
        return new BillingException(Kind.BAD_REQUEST, "validation-failed", message);
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public enum Kind {
        BAD_REQUEST,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        UNPROCESSABLE
    }
}
