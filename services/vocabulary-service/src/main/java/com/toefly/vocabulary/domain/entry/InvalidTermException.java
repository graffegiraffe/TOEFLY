package com.toefly.vocabulary.domain.entry;

/** The term is empty, too long or not a word or phrase. */
public class InvalidTermException extends RuntimeException {

    public InvalidTermException(String message) {
        super(message);
    }
}
