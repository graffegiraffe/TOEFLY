package com.toefly.profile.domain.privacy;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** Deletion needs a fresh sign-in (R-12, {@code auth_time} at most five minutes old). */
@ProblemType(value = "forbidden", status = 403)
public class ReauthenticationRequiredException extends RuntimeException implements ProblemProperties {

    public ReauthenticationRequiredException() {
        super("Sign in again to confirm this action.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("reason", "REAUTHENTICATION_REQUIRED");
    }
}
