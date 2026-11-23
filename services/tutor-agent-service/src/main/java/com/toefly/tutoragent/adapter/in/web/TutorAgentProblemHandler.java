package com.toefly.tutoragent.adapter.in.web;

import com.toefly.common.web.Problems;
import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.DependencyUnavailableException;
import com.toefly.tutoragent.application.port.in.EntitlementRequiredException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problems of tutor-agent-service (docs/05 §6.8). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TutorAgentProblemHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> api(ApiException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return response.body(problem(exception.status(), exception.slug(), exception.getMessage()));
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> entitlement(EntitlementRequiredException exception) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "entitlement-required", exception.getMessage());
        problem.setProperty("entitlement", exception.entitlement());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> ai(AiUnavailableException exception) {
        ProblemDetail problem = problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ai-unavailable",
                "Toefly Tutor is unavailable right now. Try again soon.");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        if (exception.retryAfter() != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(exception.retryAfter().toSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> dependency(DependencyUnavailableException exception) {
        ProblemDetail problem =
                problem(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable", "A dependency did not answer.");
        problem.setProperty("dependency", exception.dependency());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    static ProblemDetail problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return problem;
    }
}
