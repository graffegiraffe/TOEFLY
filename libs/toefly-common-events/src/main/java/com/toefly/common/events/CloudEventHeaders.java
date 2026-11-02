package com.toefly.common.events;

/** Kafka header names of the CloudEvents 1.0 binary mode mapping (docs/05-api-contracts.md §8.2). */
public final class CloudEventHeaders {

    public static final String SPEC_VERSION = "ce_specversion";
    public static final String ID = "ce_id";
    public static final String SOURCE = "ce_source";
    public static final String TYPE = "ce_type";
    public static final String SUBJECT = "ce_subject";
    public static final String TIME = "ce_time";
    public static final String DATA_SCHEMA = "ce_dataschema";
    public static final String CORRELATION_ID = "ce_correlationid";
    public static final String CAUSATION_ID = "ce_causationid";
    public static final String PARTITION_KEY = "ce_partitionkey";
    public static final String CONTENT_TYPE = "content-type";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";

    public static final String ATTEMPT = "x-toefly-attempt";
    public static final String TARGET_GROUP = "x-toefly-target-group";
    public static final String NOT_BEFORE = "x-toefly-not-before";
    public static final String REPLAYED_BY = "x-toefly-replayed-by";

    public static final String SPEC_VERSION_VALUE = "1.0";
    public static final String AVRO_CONTENT_TYPE = "application/avro";

    private CloudEventHeaders() {}
}
