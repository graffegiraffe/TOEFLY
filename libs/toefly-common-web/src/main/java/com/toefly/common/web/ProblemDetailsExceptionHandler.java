package com.toefly.common.web;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every error as {@code application/problem+json} with TOEFLY extensions (docs/05-api-contracts.md §3). */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    @ExceptionHandler
    ResponseEntity<Object> handleCatalogProblem(RuntimeException exception, WebRequest request) {
        ProblemType type = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ProblemType.class);
        if (type == null) {
            return handleUnexpected(exception, request);
        }
        HttpStatus status = HttpStatus.valueOf(type.status());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setType(Problems.type(type.value()));
        problem.setTitle(type.title().isEmpty() ? Problems.title(type.value()) : type.title());
        if (exception instanceof ProblemProperties properties) {
            properties.problemProperties().forEach(problem::setProperty);
        }
        return createResponseEntity(problem, new HttpHeaders(), status, request);
    }

    @ExceptionHandler
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation ->
                        error("/" + violation.getPropertyPath().toString().replace('.', '/'), violation.getMessage()))
                .toList();
        return validationFailed(errors, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> error(pointer(field), String.valueOf(field.getDefaultMessage())))
                .toList();
        return validationFailed(errors, request);
    }

    private ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unexpected error", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be processed.");
        problem.setType(Problems.type("internal-error"));
        problem.setTitle("Internal error");
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<Object> validationFailed(List<Map<String, String>> errors, WebRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request has invalid fields.");
        problem.setType(Problems.type("validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem, statusCode);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static void enrich(ProblemDetail problem, HttpStatusCode status) {
        URI type = problem.getType();
        if (type == null || !type.toString().startsWith(Problems.BASE_URI)) {
            type = Problems.type(defaultSlug(status));
            problem.setType(type);
        }
        String uri = type.toString();
        String slug = uri.substring(Problems.BASE_URI.length());
        problem.setProperty("code", Problems.code(slug));
        putIfPresent(problem, "requestId", MDC.get(RequestIds.MDC_KEY));
        putIfPresent(problem, "traceId", MDC.get(RequestIds.TRACE_ID_MDC_KEY));
    }

    private static String defaultSlug(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "malformed-request";
            case 401 -> "unauthenticated";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 409 -> "conflict";
            case 412 -> "version-mismatch";
            case 413 -> "upload-too-large";
            case 415 -> "unsupported-media-type";
            case 428 -> "precondition-required";
            case 429 -> "rate-limited";
            case 503 -> "dependency-unavailable";
            default -> "internal-error";
        };
    }

    private static void putIfPresent(ProblemDetail problem, String name, @Nullable String value) {
        if (value != null) {
            problem.setProperty(name, value);
        }
    }

    private static String pointer(FieldError field) {
        return "/" + field.getField().replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
    }

    private static Map<String, String> error(String pointer, String message) {
        return Map.of("pointer", pointer, "code", "INVALID", "message", message);
    }
}
