package com.toefly.planning.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** If-Match does not match the version of the resource (docs/05-api-contracts.md §2.7). */
@ProblemType(value = "version-mismatch", status = 412, title = "Resource version mismatch")
public class VersionMismatchException extends RuntimeException implements ProblemProperties {

    private final int currentVersion;

    public VersionMismatchException(int currentVersion) {
        super("The resource has changed; reload it and try again.");
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("currentVersion", currentVersion);
    }
}
