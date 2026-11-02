package com.toefly.common.events;

import org.apache.avro.specific.SpecificRecord;

/** Serializes the Avro body of an event the way consumers expect to read it from Kafka. */
public interface EventPayloadSerializer {

    SerializedPayload serialize(String topic, SpecificRecord record);

    /**
     * @param bytes Avro bytes in the Apicurio SerDe wire format
     * @param dataSchema value of {@code ce_dataschema}
     */
    record SerializedPayload(byte[] bytes, String dataSchema) {}
}
