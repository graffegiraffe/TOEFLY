package com.toefly.profile.domain.consent;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** Consents given at sign-up cannot be withdrawn while the account exists; the user deletes the account instead. */
@ProblemType(value = "business-rule-violated", status = 422)
public class RequiredConsentException extends RuntimeException implements ProblemProperties {

    private final ConsentType type;

    public RequiredConsentException(ConsentType type) {
        super("This consent is required for an account. Delete the account to withdraw it.");
        this.type = type;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", "required-consent", "consentType", type.name());
    }
}
