package com.toefly.vocabulary.application.port.in;

/** A refused request with its Problem Details slug (docs/05 §3). */
public class VocabularyException extends RuntimeException {

    private final Kind kind;
    private final String code;

    public VocabularyException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static VocabularyException notFound(String message) {
        return new VocabularyException(Kind.NOT_FOUND, "not-found", message);
    }

    public static VocabularyException conflict(String code, String message) {
        return new VocabularyException(Kind.CONFLICT, code, message);
    }

    public static VocabularyException invalid(String message) {
        return new VocabularyException(Kind.BAD_REQUEST, "validation-failed", message);
    }

    public static VocabularyException unprocessable(String code, String message) {
        return new VocabularyException(Kind.UNPROCESSABLE, code, message);
    }

    public static VocabularyException preconditionFailed() {
        return new VocabularyException(
                Kind.PRECONDITION_FAILED, "precondition-failed", "The word was changed by another request.");
    }

    public static VocabularyException aiUnavailable() {
        return new VocabularyException(
                Kind.UNAVAILABLE, "ai-unavailable", "Word suggestions are not available right now. Try again later.");
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public enum Kind {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_FAILED,
        UNPROCESSABLE,
        UNAVAILABLE
    }
}
