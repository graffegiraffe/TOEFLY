package com.toefly.common.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractVerifierTest {

    static final Path SAMPLE = Path.of("src/test/resources/contracts/sample");
    static final String ID = "7c0d6c3a-0f0e-4d8b-9a52-2b6a3f0d4e11";

    static Contract contract(String name) {
        return Contracts.load(SAMPLE).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    static ContractExchanger answering(int status, String body) {
        return request -> new ContractExchanger.ContractResponse(
                status, Map.of("Content-Type", List.of("application/json;charset=UTF-8")), body);
    }

    @Test
    @DisplayName("ADR-0031 the YAML subset is parsed into REST and message contracts")
    void parses() {
        assertThat(Contracts.load(SAMPLE)).extracting(Contract::name).containsExactly("create_item", "item_created");
        Contract rest = contract("create_item");
        assertThat(rest.isRest()).isTrue();
        assertThat(rest.request().headerRegex()).containsEntry("Authorization", "Bearer .+");
        assertThat(contract("item_created").triggerMethod()).isEqualTo("createItem");
    }

    @Test
    @DisplayName("ADR-0031 a response with the contract values and extra fields fulfils the contract")
    void extraFieldsAllowed() throws Exception {
        ContractTests.verifyRest(
                contract("create_item"),
                answering(
                        201,
                        "{\"id\":\"" + ID + "\",\"title\":\"Photosynthesis\",\"score\":1.0,\"tags\":[\"X\"],"
                                + "\"extra\":true}"));
    }

    @Test
    @DisplayName("ADR-0031 the producer is signed by the exchanger, not by the contract token")
    void authorizationLeftToExchanger() throws Exception {
        ContractTests.verifyRest(contract("create_item"), request -> {
            assertThat(request.headers()).doesNotContainKey("Authorization").containsKey("Content-Type");
            assertThat(request.body()).contains("Photosynthesis");
            return answering(201, "{\"id\":\"" + ID + "\",\"title\":\"Photosynthesis\",\"score\":1,\"tags\":[\"A\"]}")
                    .exchange(request);
        });
    }

    @Test
    @DisplayName("ADR-0031 every difference is reported with its path")
    void differencesReported() {
        assertThatThrownBy(() -> ContractTests.verifyRest(
                        contract("create_item"),
                        answering(200, "{\"id\":\"not-a-uuid\",\"title\":\"Biology\",\"score\":2,\"tags\":[]}")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("status: expected 201, got 200")
                .hasMessageContaining("$.id: not-a-uuid does not match")
                .hasMessageContaining("$.title: expected Photosynthesis, got Biology")
                .hasMessageContaining("$.score: expected 1, got 2")
                .hasMessageContaining("$.tags: 0 items, at least 1 expected");
    }

    @Test
    @DisplayName("ADR-0031 a missing field and a wrong type break the contract")
    void missingAndWrongType() {
        assertThat(BodyComparator.differences(Map.of("a", Map.of("b", 1)), Map.of("a", "text"), List.of()))
                .containsExactly("$.a: expected an object, got text");
        assertThat(BodyComparator.differences(Map.of("a", "1"), Map.of("a", 1), List.of()))
                .containsExactly("$.a: expected 1, got 1");
        assertThat(BodyComparator.differences(Map.of("a", 1), Map.of(), List.of()))
                .containsExactly("$.a: missing, expected 1");
    }

    @Test
    @DisplayName("ADR-0031 a message contract triggers the action and checks headers and body")
    void message() throws Exception {
        MessageReceiver receiver = new MessageReceiver() {
            boolean triggered;

            @Override
            public Object mark(String topic) {
                return "before";
            }

            @Override
            public ContractMessage receive(String topic, Object mark) {
                assertThat(mark).isEqualTo("before");
                return new ContractMessage(
                        topic,
                        "key",
                        Map.of("ce_type", "toefly.demo.item-created.v1"),
                        Map.of("itemId", ID, "title", "Photosynthesis"));
            }
        };
        Triggers triggers = new Triggers();
        ContractTests.verifyMessage(contract("item_created"), triggers, receiver);
        assertThat(triggers.calls).isEqualTo(1);
    }

    static final class Triggers {
        int calls;

        void createItem() {
            calls++;
        }
    }

    @Test
    @DisplayName("ADR-0031 the stub answers a fulfilling request and rejects any other with 599")
    void stubServer() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        try (ContractStubServer stub = ContractStubServer.start(SAMPLE)) {
            HttpResponse<String> ok = http.send(
                    HttpRequest.newBuilder(URI.create(stub.baseUrl() + "/internal/v1/items"))
                            .header("Authorization", "Bearer abc")
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"userId\":\"0b6f1c0e-3b0c-4f3e-9d57-6a3f1c1d7a11\",\"title\":\"Photosynthesis\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ok.statusCode()).isEqualTo(201);
            assertThat(ok.body()).contains("Photosynthesis");
            stub.assertAllCalled();

            HttpResponse<String> rejected = http.send(
                    HttpRequest.newBuilder(URI.create(stub.baseUrl() + "/internal/v1/items"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"userId\":\"nope\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(ContractStubServer.NO_CONTRACT);
            assertThat(rejected.body()).contains("header Authorization").contains("$.userId");
            assertThatThrownBy(stub::assertAllCalled).hasMessageContaining("rejected requests");
        }
    }

    @Test
    @DisplayName("docs/11 §8.5 a published topic without a message contract fails the coverage check")
    void coverage() {
        ContractCoverage.assertMessagesCover(SAMPLE, List.of("toefly.demo.item-created.v1"));
        assertThatThrownBy(() -> ContractCoverage.assertMessagesCover(SAMPLE, List.of("toefly.demo.other.v1")))
                .hasMessageContaining("toefly.demo.other.v1");
        ContractCoverage.assertCallersCover(SAMPLE.getParent(), List.of("sample"));
        assertThatThrownBy(() -> ContractCoverage.assertCallersCover(SAMPLE.getParent(), List.of("profile-service")))
                .hasMessageContaining("profile-service");
    }
}
