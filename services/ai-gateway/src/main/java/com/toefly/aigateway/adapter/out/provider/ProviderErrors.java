package com.toefly.aigateway.adapter.out.provider;

import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.HttpStatusCodeException;

/** Maps exceptions of the OpenAI SDK, RestClient and Spring AI to the three kinds of {@link ProviderFailure}. */
final class ProviderErrors {

    private ProviderErrors() {}

    static ProviderFailure map(String provider, Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ProviderFailure failure) {
                return failure;
            }
            if (cause instanceof RateLimitException limited) {
                return rateLimited(provider, retryAfter(limited.headers().values("retry-after")), cause);
            }
            if (cause instanceof OpenAIServiceException service) {
                return byStatus(
                        provider,
                        service.statusCode(),
                        retryAfter(service.headers().values("retry-after")),
                        cause);
            }
            if (cause instanceof HttpStatusCodeException http) {
                List<String> header = http.getResponseHeaders() == null
                        ? List.of()
                        : http.getResponseHeaders().getOrEmpty("Retry-After");
                return byStatus(provider, http.getStatusCode().value(), retryAfter(header), cause);
            }
            if (cause instanceof NonTransientAiException) {
                return new ProviderFailure(
                        ProviderFailure.Kind.REJECTED, provider + ": " + cause.getMessage(), null, cause);
            }
        }
        return ProviderFailure.unavailable(provider + ": " + error.getMessage(), error);
    }

    static ProviderFailure byStatus(
            String provider, int status, @Nullable Duration retryAfter, @Nullable Throwable cause) {
        if (status == 429) {
            return rateLimited(provider, retryAfter, cause);
        }
        if (status >= 500 || status == 408) {
            return ProviderFailure.unavailable(provider + " answered " + status, cause);
        }
        return new ProviderFailure(ProviderFailure.Kind.REJECTED, provider + " answered " + status, null, cause);
    }

    static @Nullable Duration retryAfter(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(values.getFirst().strip());
            return Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (NumberFormatException notSeconds) {
            return null;
        }
    }

    private static ProviderFailure rateLimited(
            String provider, @Nullable Duration retryAfter, @Nullable Throwable cause) {
        return new ProviderFailure(ProviderFailure.Kind.RATE_LIMITED, provider + " answered 429", retryAfter, cause);
    }
}
