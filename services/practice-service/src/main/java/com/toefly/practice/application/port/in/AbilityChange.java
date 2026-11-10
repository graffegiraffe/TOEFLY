package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.Section;

/** Theta of a section before and after one attempt. */
public record AbilityChange(Section section, double thetaBefore, double thetaAfter, double standardError) {}
