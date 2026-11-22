package com.toefly.billing.adapter.out.client;

import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.port.out.ClassroomUnavailableException;
import com.toefly.billing.domain.plan.PlanTierCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * classroom-service internal API (docs/05 §9.3.6), client credentials {@code svc-billing-service}. Called only from
 * saga steps, so there is no retry here: the orchestrator retries with backoff (docs/05 §9.2).
 */
public class HttpClassroomAdapter implements ClassroomPort {

    private final RestClient client;

    public HttpClassroomAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public void putWorkspace(UUID tutorId, boolean active, int seatLimit, PlanTierCode tier) {
        try {
            client.put()
                    .uri("/internal/v1/tutors/{tutorId}/workspace", tutorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "state",
                            active ? "ACTIVE" : "SUSPENDED",
                            "seatLimit",
                            seatLimit,
                            "planTierCode",
                            tier.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException failure) {
            throw new ClassroomUnavailableException("classroom workspace call failed", failure);
        }
    }

    @Override
    public List<UUID> enrollments(UUID tutorId) {
        try {
            JsonNode body = client.get()
                    .uri("/internal/v1/tutors/{tutorId}/enrollments", tutorId)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return List.of();
            }
            return body.path("items")
                    .valueStream()
                    .map(item -> UUID.fromString(item.path("studentId").asString()))
                    .toList();
        } catch (RestClientException failure) {
            throw new ClassroomUnavailableException("classroom enrollments call failed", failure);
        }
    }
}
