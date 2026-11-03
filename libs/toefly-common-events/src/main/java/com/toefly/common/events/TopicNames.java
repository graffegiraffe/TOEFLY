package com.toefly.common.events;

import java.util.regex.Pattern;

/** Canonical Kafka topic names: {@code toefly.<service>.<event-name>.v<N>} plus {@code .retry} and {@code .dlq}. */
public final class TopicNames {

    public static final String RETRY_SUFFIX = ".retry";
    public static final String DLQ_SUFFIX = ".dlq";

    private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern TOPIC =
            Pattern.compile("toefly\\.[a-z][a-z0-9]*(-[a-z0-9]+)*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*\\.v[1-9][0-9]*");

    private TopicNames() {}

    public static String topic(String service, String eventName, int version) {
        requireSegment(service, "service");
        requireSegment(eventName, "eventName");
        if (version < 1) {
            throw new IllegalArgumentException("Topic version must be positive: " + version);
        }
        return "toefly." + service + "." + eventName + ".v" + version;
    }

    public static boolean isValid(String topic) {
        return TOPIC.matcher(topic).matches();
    }

    public static String requireValid(String topic) {
        if (!isValid(topic)) {
            throw new IllegalArgumentException("Not a canonical TOEFLY topic: " + topic);
        }
        return topic;
    }

    public static String retry(String topic) {
        return requireValid(topic) + RETRY_SUFFIX;
    }

    public static String dlq(String topic) {
        return requireValid(topic) + DLQ_SUFFIX;
    }

    /** Apicurio artifact id of the value schema under {@code TopicIdStrategy}. */
    public static String valueArtifactId(String topic) {
        return requireValid(topic) + "-value";
    }

    /** Logical schema reference stored in {@code ce_dataschema}: {@code apicurio://toefly/<topic>-value/<version>}. */
    public static String dataSchema(String topic, int schemaVersion) {
        return "apicurio://toefly/" + valueArtifactId(topic) + "/" + schemaVersion;
    }

    private static void requireSegment(String value, String name) {
        if (!SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be kebab-case: " + value);
        }
    }
}
