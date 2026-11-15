package com.toefly.aigateway.domain.call;

/**
 * Paid price of a model in US dollars (§10): per million input and output tokens, per hour of audio, per million
 * characters. Free calls are still priced, so the dashboards show the shadow cost (§2.7).
 */
public record ModelPrice(double inputPerMillion, double outputPerMillion, double perAudioHour, double perMillionChars) {

    public static final ModelPrice FREE = new ModelPrice(0, 0, 0, 0);

    /** Shadow cost in millionths of a dollar. */
    public long microUsd(TokenUsage usage) {
        double usd = usage.inputTokens() * inputPerMillion / 1_000_000
                + usage.outputTokens() * outputPerMillion / 1_000_000
                + usage.audioSeconds() * perAudioHour / 3600
                + usage.characters() * perMillionChars / 1_000_000;
        return Math.round(usd * 1_000_000);
    }
}
