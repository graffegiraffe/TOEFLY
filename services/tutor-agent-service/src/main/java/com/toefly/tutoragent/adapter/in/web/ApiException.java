package com.toefly.tutoragent.adapter.in.web;

import org.springframework.http.HttpStatus;

/** A problem of this API with its slug (docs/05 §4). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;

    public ApiException(HttpStatus status, String slug, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "The resource does not exist.");
    }

    public static ApiException notImplemented(String phase) {
        return new ApiException(
                HttpStatus.NOT_IMPLEMENTED, "not-implemented", "This operation arrives in phase " + phase + ".");
    }
}
