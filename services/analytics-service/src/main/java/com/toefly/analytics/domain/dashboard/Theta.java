package com.toefly.analytics.domain.dashboard;

import java.time.Instant;

/** Ability θ of one section (logit) with its standard error. */
public record Theta(double value, double standardError, Instant asOf) {}
