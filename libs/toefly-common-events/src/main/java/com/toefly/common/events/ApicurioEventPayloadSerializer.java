package com.toefly.common.events;

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Apicurio {@code AvroKafkaSerializer} with {@code TopicIdStrategy}; the schema id travels inside the payload. The
 * registry version of the writer schema is resolved once per topic and schema for {@code ce_dataschema}.
 */
public class ApicurioEventPayloadSerializer implements EventPayloadSerializer, AutoCloseable {

    private final AvroKafkaSerializer<SpecificRecord> serializer = new AvroKafkaSerializer<>();
    private final RestClient registry;
    private final Map<String, String> dataSchemas = new ConcurrentHashMap<>();

    public ApicurioEventPayloadSerializer(ToeflyEventsProperties properties) {
        serializer.configure(
                Map.of(
                        SerdeConfig.REGISTRY_URL,
                        properties.registryUrl(),
                        SerdeConfig.AUTO_REGISTER_ARTIFACT,
                        properties.autoRegister(),
                        SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID,
                        "toefly",
                        KafkaSerdeConfig.ENABLE_HEADERS,
                        false),
                false);
        this.registry = RestClient.create(properties.registryUrl());
    }

    @Override
    public SerializedPayload serialize(String topic, SpecificRecord record) {
        byte[] bytes = serializer.serialize(TopicNames.requireValid(topic), record);
        String schema = record.getSchema().toString();
        String dataSchema = dataSchemas.computeIfAbsent(topic + '\n' + schema, key -> resolve(topic, schema));
        return new SerializedPayload(bytes, dataSchema);
    }

    private String resolve(String topic, String schema) {
        VersionSearchResults found = registry.post()
                .uri(uri -> uri.path("/search/versions")
                        .queryParam("canonical", true)
                        .queryParam("artifactType", "AVRO")
                        .queryParam("groupId", "toefly")
                        .queryParam("artifactId", TopicNames.valueArtifactId(topic))
                        .build())
                // The registry gzips responses for clients that accept it, and the JDK client does not inflate them.
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .contentType(MediaType.APPLICATION_JSON)
                .body(schema)
                .retrieve()
                .body(VersionSearchResults.class);
        if (found == null || found.versions() == null || found.versions().isEmpty()) {
            // The serde registers named types of other namespaces (shared enums) as references, so the stored
            // content differs from the inlined schema; the version the serde just resolved is the latest one.
            found = latestVersion(topic);
        }
        if (found == null || found.versions() == null || found.versions().isEmpty()) {
            throw new IllegalStateException("Schema of " + topic + " is not registered in Apicurio");
        }
        String version = found.versions().getLast().version();
        return TopicNames.dataSchema(topic, Integer.parseInt(version));
    }

    private VersionSearchResults latestVersion(String topic) {
        return registry.get()
                .uri(uri -> uri.path("/search/versions")
                        .queryParam("groupId", "toefly")
                        .queryParam("artifactId", TopicNames.valueArtifactId(topic))
                        .queryParam("orderby", "globalId")
                        .queryParam("order", "desc")
                        .queryParam("limit", 1)
                        .build())
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .body(VersionSearchResults.class);
    }

    @Override
    public void close() {
        serializer.close();
    }

    record VersionSearchResults(List<Version> versions) {}

    record Version(String version) {}
}
