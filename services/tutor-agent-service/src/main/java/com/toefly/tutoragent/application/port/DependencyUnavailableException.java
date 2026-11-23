package com.toefly.tutoragent.application.port;

/** A called service did not answer in time or answered with a server error. */
public class DependencyUnavailableException extends RuntimeException {

    private final String dependency;

    public DependencyUnavailableException(String dependency, String message, Throwable cause) {
        super(message, cause);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
