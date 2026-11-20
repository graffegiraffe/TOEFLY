package com.toefly.analytics.application.port.in;

import java.util.Arrays;
import java.util.Optional;

/** Read models an admin can rebuild from the journal; the path segment is the kebab-case name. */
public enum ProjectionName {
    STUDENT_DASHBOARD("student-dashboard"),
    ACTIVITY("activity"),
    PRODUCT_METRICS("product-metrics"),
    AI_USAGE("ai-usage");

    private final String path;

    ProjectionName(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static Optional<ProjectionName> fromPath(String path) {
        return Arrays.stream(values()).filter(p -> p.path.equals(path)).findFirst();
    }
}
