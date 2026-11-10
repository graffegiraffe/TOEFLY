package com.toefly.practice.adapter.in.web;

import com.toefly.common.web.Problems;
import com.toefly.practice.application.port.in.VersionMismatchException;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Problems specific to the headers of this API: a missing If-Match or Idempotency-Key is 428, a stale If-Match is
 * 412 with the current ETag (docs/05-api-contracts.md §2.6, §2.7).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PracticeProblemHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException exception) {
        String header = exception.getHeaderName().toLowerCase(Locale.ROOT);
        String slug = switch (header) {
            case "if-match" -> "precondition-required";
            case "idempotency-key" -> "idempotency-key-required";
            default -> "validation-failed";
        };
        HttpStatus status =
                slug.equals("validation-failed") ? HttpStatus.BAD_REQUEST : HttpStatus.PRECONDITION_REQUIRED;
        return ResponseEntity.status(status)
                .body(problem(status, slug, "Send the " + exception.getHeaderName() + " header."));
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> versionMismatch(VersionMismatchException exception) {
        ProblemDetail problem = problem(HttpStatus.PRECONDITION_FAILED, "version-mismatch", exception.getMessage());
        problem.setTitle("Resource version mismatch");
        problem.setProperty("currentVersion", exception.currentVersion());
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .eTag(ETags.of(exception.currentVersion()))
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return problem;
    }
}
