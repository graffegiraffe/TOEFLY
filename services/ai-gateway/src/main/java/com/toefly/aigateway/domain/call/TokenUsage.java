package com.toefly.aigateway.domain.call;

/** What one call consumed. */
public record TokenUsage(int inputTokens, int outputTokens, double audioSeconds, int characters) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

    public static TokenUsage tokens(int input, int output) {
        return new TokenUsage(input, output, 0, 0);
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
