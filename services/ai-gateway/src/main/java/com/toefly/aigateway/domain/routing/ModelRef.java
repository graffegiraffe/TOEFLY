package com.toefly.aigateway.domain.routing;

import java.util.Objects;

/** One model of one provider, e.g. {@code GROQ openai/gpt-oss-120b}. */
public record ModelRef(AiProvider provider, String model) {

    public ModelRef {
        Objects.requireNonNull(provider, "provider");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is blank");
        }
    }

    /** Parses {@code GROQ:openai/gpt-oss-120b}. */
    public static ModelRef parse(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("expected PROVIDER:model, got " + value);
        }
        return new ModelRef(
                AiProvider.valueOf(value.substring(0, colon).trim()),
                value.substring(colon + 1).trim());
    }

    /** Model family for the cache key: versions of one family answer alike (§2.6). */
    public String family() {
        String name = model.contains("/") ? model.substring(model.indexOf('/') + 1) : model;
        int tag = name.indexOf(':');
        return provider + ":" + (tag > 0 ? name.substring(0, tag) : name);
    }

    @Override
    public String toString() {
        return provider + ":" + model;
    }
}
