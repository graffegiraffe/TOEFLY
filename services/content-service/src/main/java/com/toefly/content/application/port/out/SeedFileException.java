package com.toefly.content.application.port.out;

public class SeedFileException extends RuntimeException {

    public SeedFileException(String message) {
        super(message);
    }

    public SeedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
