package com.toefly.aigateway.domain.quota;

/** What one call takes from the buckets before it runs; settled with the actual tokens afterwards. */
public record QuotaDemand(long requests, long tokens, double audioSeconds, long characters) {

    public static QuotaDemand tokens(long tokens) {
        return new QuotaDemand(1, tokens, 0, 0);
    }

    public static QuotaDemand audio(double seconds) {
        return new QuotaDemand(1, 0, seconds, 0);
    }

    public static QuotaDemand characters(long characters) {
        return new QuotaDemand(1, 0, 0, characters);
    }

    public double need(QuotaDimension dimension) {
        return switch (dimension.unit()) {
            case REQUESTS -> requests;
            case TOKENS -> tokens;
            case AUDIO_SECONDS -> audioSeconds;
            case CHARACTERS -> characters;
        };
    }
}
