package com.toefly.common.test.contract;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer side of REST contracts (ADR-0031): a stub of the producer that answers only requests fulfilling a
 * contract; any other request gets 599 with the differences, so the consumer test fails.
 */
public final class ContractStubServer implements AutoCloseable {

    static final int NO_CONTRACT = 599;

    private final HttpServer server;
    private final List<Contract> contracts;
    private final Set<String> called = ConcurrentHashMap.newKeySet();
    private final List<String> rejected = new ArrayList<>();

    private ContractStubServer(List<Contract> contracts) throws IOException {
        this.contracts = contracts;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** Starts a stub with the REST contracts of the directory, for example contracts of another module. */
    public static ContractStubServer start(Path directory) {
        try {
            return new ContractStubServer(
                    Contracts.load(directory).stream().filter(Contract::isRest).toList());
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the contract stub", e);
        }
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort();
    }

    /** Fails unless every contract was called at least once and no request was rejected. */
    public void assertAllCalled() {
        List<String> missing = contracts.stream()
                .map(Contract::name)
                .filter(name -> !called.contains(name))
                .toList();
        synchronized (rejected) {
            if (!missing.isEmpty() || !rejected.isEmpty()) {
                throw new AssertionError("Contracts not called: " + missing + "; rejected requests: " + rejected);
            }
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String url = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null
                        ? ""
                        : "?" + exchange.getRequestURI().getRawQuery());
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(exchange.getRequestHeaders());
        List<String> report = new ArrayList<>();
        for (Contract contract : contracts) {
            List<String> differences = differences(contract.request(), exchange.getRequestMethod(), url, headers, body);
            if (differences.isEmpty()) {
                called.add(contract.name());
                respond(exchange, contract.response());
                return;
            }
            report.add(contract.name() + ": " + String.join("; ", differences));
        }
        String message = exchange.getRequestMethod() + " " + url + " fulfils no contract: " + report;
        synchronized (rejected) {
            rejected.add(message);
        }
        send(exchange, NO_CONTRACT, Map.of("Content-Type", "text/plain"), message);
    }

    private static List<String> differences(
            Contract.Request request, String method, String url, Map<String, List<String>> headers, String body) {
        List<String> differences = new ArrayList<>();
        if (!request.method().equalsIgnoreCase(method)) {
            differences.add("method " + method + " instead of " + request.method());
        }
        if (!request.url().equals(url)) {
            differences.add("url " + url + " instead of " + request.url());
        }
        Map<String, String> expectedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        expectedHeaders.putAll(request.headers());
        differences.addAll(HeaderComparator.differences(expectedHeaders, request.headerRegex(), headers));
        if (request.body() != null || !request.bodyMatchers().isEmpty()) {
            differences.addAll(
                    BodyComparator.differences(request.body(), ContractTests.parse(body), request.bodyMatchers()));
        }
        return differences;
    }

    private static void respond(HttpExchange exchange, Contract.Response response) throws IOException {
        send(
                exchange,
                response.status(),
                response.headers(),
                response.body() == null ? "" : ContractTests.json(response.body()));
    }

    private static void send(HttpExchange exchange, int status, Map<String, String> headers, String body)
            throws IOException {
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
