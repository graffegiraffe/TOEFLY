package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.application.port.in.RetryAfter;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Turns the problems of the gateway into RFC 9457 bodies, also for SSE {@code error} events. */
@Component
public class ProblemMapper {

    public ProblemDetail toProblem(Throwable failure) {
        ProblemType type = AnnotatedElementUtils.findMergedAnnotation(failure.getClass(), ProblemType.class);
        if (type != null) {
            ProblemDetail problem = problem(HttpStatus.valueOf(type.status()), type.value(), failure.getMessage());
            if (failure instanceof ProblemProperties properties) {
                properties.problemProperties().forEach(problem::setProperty);
            }
            return problem;
        }
        if (failure instanceof PromptRuleException rule) {
            return problem(status(rule.code()), rule.code(), rule.getMessage());
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error.");
    }

    static HttpStatus status(String code) {
        return switch (code) {
            case "not-found", "prompt-not-found" -> HttpStatus.NOT_FOUND;
            case "conflict" -> HttpStatus.CONFLICT;
            case "validation-failed" -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    static ProblemDetail problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://toefly.app/problems/" + slug));
        problem.setTitle(title(slug));
        problem.setProperty("code", slug.toUpperCase(Locale.ROOT).replace('-', '_'));
        return problem;
    }

    static long retryAfter(Throwable failure) {
        return failure instanceof RetryAfter retry ? retry.retryAfterSeconds() : 0;
    }

    private static String title(String slug) {
        String words = slug.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
