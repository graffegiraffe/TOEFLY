package com.toefly.common.test.contract;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Runs the request of a contract against the producer and returns what it answered. */
@FunctionalInterface
public interface ContractExchanger {

    ContractResponse exchange(ContractRequest request) throws Exception;

    /**
     * The request of a contract. {@code Authorization} of the contract is left out: the exchanger signs the request
     * as the consumer it tests (a service token of that consumer).
     */
    record ContractRequest(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable String body) {}

    record ContractResponse(int status, Map<String, List<String>> headers, String body) {}
}
