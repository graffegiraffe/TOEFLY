package com.toefly.planning.adapter.in.web;

import com.toefly.common.web.Problems;
import com.toefly.planning.application.port.in.VersionMismatchException;
import com.toefly.planning.domain.week.RebalanceTooSoonException;
import com.toefly.planning.domain.week.WeekRuleException;
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
 * Problems of this API: missing headers (428), a stale If-Match (412 with the ETag), the rules of the week (404,
 * 409, 422) and the 48-hour limit of a rebalance with {@code nextAllowedAt} (docs/05-api-contracts.md §7.2).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PlanningProblemHandler {

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

    @ExceptionHandler
    ResponseEntity<ProblemDetail> tooSoon(RebalanceTooSoonException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "conflict", exception.getMessage());
        problem.setProperty("nextAllowedAt", exception.nextAllowedAt().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> weekRule(WeekRuleException exception) {
        HttpStatus status = switch (exception.code()) {
            case "not-found" -> HttpStatus.NOT_FOUND;
            case "validation-failed" -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(problem(status, exception.code(), exception.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return problem;
    }
}
