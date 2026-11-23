package com.toefly.tutoragent.adapter.out.client;

import com.toefly.tutoragent.application.port.out.PracticePort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** {@code getAttemptReviewInternal} of practice-service (docs/05 §9.3.2). */
public class PracticeClientAdapter implements PracticePort {

    private final RestClient practice;

    public PracticeClientAdapter(RestClient practice) {
        this.practice = practice;
    }

    @Override
    public AttemptReview review(UUID attemptId, @Nullable String bearer) {
        JsonNode body =
                OwnerCalls.get(practice, "practice-service", "/internal/v1/attempts/{id}/review", bearer, attemptId);
        List<ItemReview> items = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            items.add(new ItemReview(
                    UUID.fromString(item.path("itemId").asString()),
                    item.path("ordinal").asInt(),
                    item.path("correct").asBoolean(false),
                    !item.path("userAnswer").isNull()
                            && !item.path("userAnswer").isMissingNode(),
                    answer(item.path("userAnswer")),
                    answer(item.path("correctAnswer")),
                    OwnerCalls.text(item.path("explanation").path("text"))));
        }
        return new AttemptReview(attemptId, body.path("taskType").asString(""), items);
    }

    /** Typed text as is; option and fragment ids joined, since the review carries no option texts. */
    static @Nullable String answer(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return switch (value.path("type").asString("")) {
            case "TEXT" -> value.path("text").asString();
            case "CHOICE" -> join(value.path("optionIds"));
            case "SEQUENCE" -> join(value.path("fragmentIds"));
            default -> null;
        };
    }

    private static String join(JsonNode ids) {
        return StreamSupport.stream(ids.spliterator(), false)
                .map(n -> n.isNull() ? "_" : n.asString())
                .collect(Collectors.joining(" "));
    }
}
