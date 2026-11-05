package com.toefly.common.test.contract;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Exchanger of reactive services: the contract request goes through WebTestClient, signed by {@code authentication}. */
public final class WebTestClientExchanger implements ContractExchanger {

    private final WebTestClient client;
    private final Consumer<HttpHeaders> authentication;

    private WebTestClientExchanger(WebTestClient client, Consumer<HttpHeaders> authentication) {
        this.client = client;
        this.authentication = authentication;
    }

    public static WebTestClientExchanger of(WebTestClient client, Consumer<HttpHeaders> authentication) {
        return new WebTestClientExchanger(client, authentication);
    }

    @Override
    public ContractResponse exchange(ContractRequest request) {
        WebTestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(request.method()))
                .uri(request.url())
                .headers(headers -> {
                    request.headers().forEach(headers::set);
                    authentication.accept(headers);
                });
        WebTestClient.RequestHeadersSpec<?> ready =
                request.body() == null ? spec : spec.bodyValue(request.body().getBytes(StandardCharsets.UTF_8));
        EntityExchangeResult<byte[]> result = ready.exchange().expectBody().returnResult();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        result.getResponseHeaders().forEach(headers::put);
        byte[] body = result.getResponseBodyContent();
        return new ContractResponse(
                result.getStatus().value(), headers, body == null ? "" : new String(body, StandardCharsets.UTF_8));
    }
}
