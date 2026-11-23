package com.toefly.vocabulary.application.port.in;

/** A service this one calls (content-service) did not answer. */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is unavailable", cause);
    }
}
