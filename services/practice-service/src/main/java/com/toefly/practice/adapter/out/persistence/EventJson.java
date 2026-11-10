package com.toefly.practice.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AttemptEvent;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON of attempt events and snapshots. The type of an event is the {@code event_type} column; answers carry their
 * form in {@code type} as in the REST API. Every payload has {@code v} for upcasting (docs/04-architecture.md §6.6).
 */
final class EventJson {

    static final int PAYLOAD_VERSION = 1;

    static final JsonMapper MAPPER = JsonMapper.builder()
            .addMixIn(Answer.class, AnswerMixin.class)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Map<String, Class<? extends AttemptEvent>> TYPES = Map.of(
            "AttemptStarted", AttemptEvent.AttemptStarted.class,
            "AnswerRecorded", AttemptEvent.AnswerRecorded.class,
            "AnswerCleared", AttemptEvent.AnswerCleared.class,
            "AttemptPaused", AttemptEvent.AttemptPaused.class,
            "AttemptResumed", AttemptEvent.AttemptResumed.class,
            "AttemptSubmitted", AttemptEvent.AttemptSubmitted.class,
            "ItemScored", AttemptEvent.ItemScored.class,
            "AttemptScored", AttemptEvent.AttemptScored.class,
            "AttemptAbandoned", AttemptEvent.AttemptAbandoned.class);

    private EventJson() {}

    static String type(AttemptEvent event) {
        return event.getClass().getSimpleName();
    }

    static String write(AttemptEvent event) {
        Map<String, Object> tree = MAPPER.convertValue(
                event,
                MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class, String.class, Object.class));
        tree.put("v", PAYLOAD_VERSION);
        return MAPPER.writeValueAsString(tree);
    }

    /** Reads an event; older payload versions are upcast here when the format changes. */
    static AttemptEvent read(String type, String payload) {
        Class<? extends AttemptEvent> target = TYPES.get(type);
        if (target == null) {
            throw new IllegalStateException("Unknown attempt event type " + type);
        }
        return MAPPER.readValue(payload, target);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Answer.Choice.class, name = "CHOICE"),
        @JsonSubTypes.Type(value = Answer.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = Answer.Sequence.class, name = "SEQUENCE"),
        @JsonSubTypes.Type(value = Answer.Mapping.class, name = "MAPPING")
    })
    interface AnswerMixin {}
}
