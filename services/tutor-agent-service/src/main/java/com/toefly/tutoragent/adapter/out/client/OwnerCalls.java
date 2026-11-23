package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.DependencyUnavailableException;
import com.toefly.tutoragent.application.port.NotSubmittedException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One GET to an owner service (docs/05 §9.1): the learner's token when there is one, client credentials otherwise;
 * 404 and 409 become {@link NotSubmittedException}, everything else {@link DependencyUnavailableException}.
 */
final class OwnerCalls {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private OwnerCalls() {}

    static JsonNode get(
            RestClient client, String dependency, String uri, @Nullable String bearer, Object... variables) {
        return exchange(client, dependency, c -> c.get().uri(uri, variables), bearer);
    }

    static JsonNode post(RestClient client, String dependency, String uri, Object body, @Nullable String bearer) {
        return exchange(
                client,
                dependency,
                c -> c.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body),
                bearer);
    }

    private static JsonNode exchange(
            RestClient client,
            String dependency,
            Function<RestClient, RestClient.RequestHeadersSpec<?>> request,
            @Nullable String bearer) {
        try {
            RestClient.RequestHeadersSpec<?> spec = request.apply(client).accept(MediaType.APPLICATION_JSON);
            if (bearer != null) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
            }
            String raw = spec.retrieve().body(String.class);
            return raw == null || raw.isBlank() ? JSON.nullNode() : JSON.readTree(raw);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status == 404 || status == 409) {
                throw new NotSubmittedException(dependency + " answered " + status);
            }
            throw new DependencyUnavailableException(dependency, dependency + " answered " + status, failure);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException(dependency, dependency + " is unreachable", failure);
        }
    }

    static @Nullable String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
