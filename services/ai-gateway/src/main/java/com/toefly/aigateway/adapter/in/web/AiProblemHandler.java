package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.in.AiQuotaExhaustedException;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problems of this API: 429 and 503 carry {@code Retry-After} (docs/05-api-contracts.md §9.3.8). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AiProblemHandler {

    private final ProblemMapper problems;

    AiProblemHandler(ProblemMapper problems) {
        this.problems = problems;
    }

    @ExceptionHandler({AiQuotaExhaustedException.class, AiProviderUnavailableException.class})
    ResponseEntity<ProblemDetail> retryLater(RuntimeException failure) {
        ProblemDetail problem = problems.toProblem(failure);
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ProblemMapper.retryAfter(failure)))
                .body(problem);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> rule(PromptRuleException failure) {
        ProblemDetail problem = problems.toProblem(failure);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
