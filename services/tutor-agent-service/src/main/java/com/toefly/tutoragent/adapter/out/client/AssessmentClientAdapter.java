package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.out.AssessmentPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** {@code getSubmissionInternal} and {@code getBandEstimateInternal} of assessment-service. */
public class AssessmentClientAdapter implements AssessmentPort {

    private final RestClient assessment;

    public AssessmentClientAdapter(RestClient assessment) {
        this.assessment = assessment;
    }

    @Override
    public Submission submission(UUID submissionId, @Nullable Integer version, @Nullable String bearer) {
        JsonNode body = version == null
                ? OwnerCalls.get(
                        assessment, "assessment-service", "/internal/v1/submissions/{id}", bearer, submissionId)
                : OwnerCalls.get(
                        assessment,
                        "assessment-service",
                        "/internal/v1/submissions/{id}?version={v}",
                        bearer,
                        submissionId,
                        version);
        List<Version> versions = new ArrayList<>();
        for (JsonNode v : body.path("versions")) {
            JsonNode evaluation = v.path("evaluation");
            List<Comment> comments = new ArrayList<>();
            for (JsonNode c : evaluation.path("comments")) {
                comments.add(new Comment(
                        c.path("quote").asString(""),
                        c.path("comment").asString(""),
                        OwnerCalls.text(c.path("suggestion")),
                        c.path("category").asString("GRAMMAR"),
                        c.path("subcategory").asString("OTHER"),
                        c.path("severity").asString("MINOR")));
            }
            String evaluatedAt = OwnerCalls.text(v.path("evaluatedAt"));
            versions.add(new Version(
                    v.path("versionNo").asInt(),
                    v.path("status").asString(""),
                    v.path("text").asString(""),
                    v.path("wordCount").asInt(0),
                    evaluation.path("finalScore").isNumber()
                            ? evaluation.path("finalScore").asDouble()
                            : null,
                    comments,
                    evaluatedAt == null ? null : Instant.parse(evaluatedAt)));
        }
        return new Submission(
                submissionId,
                UUID.fromString(body.path("userId").asString()),
                body.path("taskType").asString(""),
                body.path("status").asString(""),
                versions,
                OwnerCalls.text(body.path("modelAnswer").path("text")));
    }

    @Override
    public Map<String, Double> bandEstimate(UUID userId, @Nullable String bearer) {
        JsonNode body = OwnerCalls.get(
                assessment, "assessment-service", "/internal/v1/users/{id}/band-estimate", bearer, userId);
        Map<String, Double> bands = new LinkedHashMap<>();
        for (JsonNode section : body.path("sections")) {
            JsonNode point = section.path("pointBand");
            if (point.isNumber()) {
                bands.put(section.path("section").asString(), point.asDouble());
            }
        }
        JsonNode overall = body.path("overall").path("pointBand");
        if (overall.isNumber()) {
            bands.put("OVERALL", overall.asDouble());
        }
        return bands;
    }
}
