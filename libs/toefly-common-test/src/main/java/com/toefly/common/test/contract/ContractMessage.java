package com.toefly.common.test.contract;

import java.util.Map;

/** A message as it would reach Kafka: topic, key, headers and the payload decoded to JSON. */
public record ContractMessage(String topic, String key, Map<String, String> headers, Object body) {}
