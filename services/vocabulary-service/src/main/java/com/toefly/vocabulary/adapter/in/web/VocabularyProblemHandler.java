package com.toefly.vocabulary.adapter.in.web;

import com.toefly.common.web.Problems;
import com.toefly.vocabulary.application.port.in.DependencyUnavailableException;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problems of the vocabulary API (docs/05 §3, §6.10). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class VocabularyProblemHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException exception) {
        String header = exception.getHeaderName().toLowerCase(Locale.ROOT);
        if (header.equals("idempotency-key")) {
            return problem(
                    HttpStatus.PRECONDITION_REQUIRED, "idempotency-key-required", "Send the Idempotency-Key header.");
        }
        if (header.equals("if-match")) {
            return problem(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "Send the If-Match header.");
        }
        return problem(
                HttpStatus.BAD_REQUEST, "validation-failed", "Send the " + exception.getHeaderName() + " header.");
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> vocabulary(VocabularyException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ResponseEntity<ProblemDetail> response = problem(status, exception.code(), exception.getMessage());
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return ResponseEntity.status(status)
                    .header(HttpHeaders.RETRY_AFTER, "60")
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> dependency(DependencyUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable", "Try again in a minute.")
                        .getBody());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> changed(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "request-in-progress", "The word was changed meanwhile. Try again.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return ResponseEntity.status(status).body(problem);
    }
}
