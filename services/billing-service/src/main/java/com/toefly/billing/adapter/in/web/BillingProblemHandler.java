package com.toefly.billing.adapter.in.web;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.InvalidSignatureException;
import com.toefly.billing.application.port.in.ProviderException;
import com.toefly.billing.domain.seat.SeatsInUseException;
import com.toefly.billing.domain.subscription.SubscriptionStateException;
import com.toefly.common.web.Problems;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problems of the billing API (docs/05 §3, §6.12). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BillingProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingProblemHandler.class);

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
    ResponseEntity<ProblemDetail> billing(BillingException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    /** T-F6-01: the webhook is rejected; only the reason is logged, never the body (docs/10 §17.3, WEBHOOK_REJECTED). */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> signature(InvalidSignatureException exception) {
        log.warn("WEBHOOK_REJECTED: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "webhook-signature-invalid", "The signature is invalid.");
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> provider(ProviderException exception) {
        log.warn("Payment provider error: {}", exception.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "payment-provider-error", "The payment provider is unavailable.");
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> state(SubscriptionStateException exception) {
        return problem(HttpStatus.CONFLICT, exception.code(), exception.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> seats(SeatsInUseException exception) {
        return problem(HttpStatus.CONFLICT, "seats-in-use", exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(Problems.type(slug));
        problem.setTitle(Problems.title(slug));
        problem.setProperty("code", Problems.code(slug));
        return ResponseEntity.status(status).body(problem);
    }
}
