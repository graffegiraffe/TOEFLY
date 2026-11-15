package com.toefly.aigateway.domain.quota;

import com.toefly.aigateway.domain.routing.ModelRef;

/** Units taken from the buckets of a model, settled when the provider reports the actual usage. */
public record Reservation(String id, ModelRef model, QuotaDemand demand) {}
