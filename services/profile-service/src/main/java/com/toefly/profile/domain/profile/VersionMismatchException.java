package com.toefly.profile.domain.profile;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** {@code If-Match} names another version of the profile (docs/05-api-contracts.md §2.7). */
@ProblemType(value = "version-mismatch", status = 412, title = "Resource version mismatch")
public class VersionMismatchException extends RuntimeException implements ProblemProperties {

    private final long currentVersion;

    public VersionMismatchException(long currentVersion) {
        super("The profile was modified by another request.");
        this.currentVersion = currentVersion;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("currentVersion", currentVersion);
    }
}
