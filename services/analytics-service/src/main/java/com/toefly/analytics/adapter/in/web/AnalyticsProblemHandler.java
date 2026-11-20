package com.toefly.analytics.adapter.in.web;

import com.toefly.analytics.application.port.in.AccessDeniedException;
import com.toefly.analytics.application.port.in.IdempotencyKeyReuseException;
import com.toefly.analytics.application.port.in.InvalidPeriodException;
import com.toefly.analytics.application.port.in.RebuildInProgressException;
import com.toefly.analytics.application.port.in.RebuildNotFoundException;
import com.toefly.common.web.Problems;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problems of this API (docs/05 §3, §6.14). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AnalyticsProblemHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException exception) {
        if (exception.getHeaderName().toLowerCase(Locale.ROOT).equals("idempotency-key")) {
            return problem(
                    HttpStatus.PRECONDITION_REQUIRED, "idempotency-key-required", "Send the Idempotency-Key header.");
        }
        return problem(
                HttpStatus.BAD_REQUEST, "validation-failed", "Send the " + exception.getHeaderName() + " header.");
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> denied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> period(InvalidPeriodException exception) {
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", exception.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> running(RebuildInProgressException exception) {
        return problem(HttpStatus.CONFLICT, "conflict", exception.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> notFound(RebuildNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "not-found", exception.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> keyReuse(IdempotencyKeyReuseException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reuse", exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return ResponseEntity.status(status).body(problem);
    }
}
