package com.toefly.profile.adapter.out.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.common.test.contract.ContractStubServer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Consumer side of the contracts of api-gateway for profile-service (ADR-0031). */
class GatewaySessionRevocationContractTest {

    static final Path GATEWAY_CONTRACTS = Path.of("../api-gateway/src/test/resources/contracts/profile-service");

    static RestClient client(String baseUrl) {
        // The service token comes from client credentials in production; any bearer fulfils the contract here.
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer service-token")
                .build();
    }

    @Test
    @DisplayName("AC-01.05.4 profile-service calls the gateway revocation as the contract describes")
    void revokeSessions() {
        try (ContractStubServer gateway = ContractStubServer.start(GATEWAY_CONTRACTS)) {
            new GatewaySessionRevocationAdapter(client(gateway.baseUrl())).revokeSessions(UUID.randomUUID());
            gateway.assertAllCalled();
        }
    }

    @Test
    @DisplayName("ADR-0031 a request outside the contract is rejected by the stub")
    void requestOutsideContractFails() {
        try (ContractStubServer gateway = ContractStubServer.start(GATEWAY_CONTRACTS)) {
            RestClient unsigned =
                    RestClient.builder().baseUrl(gateway.baseUrl()).build();
            assertThatThrownBy(() -> new GatewaySessionRevocationAdapter(unsigned).revokeSessions(UUID.randomUUID()))
                    .isInstanceOf(RestClientResponseException.class)
                    .hasMessageContaining("599");
        }
    }
}
