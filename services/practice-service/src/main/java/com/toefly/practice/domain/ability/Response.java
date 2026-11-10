package com.toefly.practice.domain.ability;

/** One scored item for theta: difficulty {@code b} in logits and outcome {@code x = score / maxScore} in [0, 1]. */
public record Response(double b, double x) {}
