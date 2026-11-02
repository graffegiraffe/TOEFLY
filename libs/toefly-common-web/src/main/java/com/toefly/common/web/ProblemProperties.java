package com.toefly.common.web;

import java.util.Map;

/** Exception with type-specific Problem Details fields, for example {@code currentVersion}. */
public interface ProblemProperties {

    Map<String, Object> problemProperties();
}
