package com.toefly.keycloak;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.authentication.requiredactions.VerifyEmail;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

/**
 * Built-in Verify Email with a cap of five resend requests an hour (AC-01.02.4). Keycloak itself keeps the 60-second
 * cooldown between emails (required action config {@code emailResendCooldownSeconds}). The provider keeps the
 * built-in id and replaces it through a higher {@link #order()}.
 */
public class HourlyLimitedVerifyEmail extends VerifyEmail {

    static final int RESENDS_PER_HOUR = 5;
    static final String LIMIT_MESSAGE = "toeflyEmailResendHourlyLimit";

    @Override
    public void processAction(RequiredActionContext context) {
        HourlyLimit limit = new HourlyLimit(new SingleUseObjectStore(context.getSession()), RESENDS_PER_HOUR);
        if (!limit.tryAcquire("toefly-verify-email-resend:" + context.getUser().getId())) {
            context.getEvent().error("email_resend_limit");
            context.challenge(context.form()
                    .setError(LIMIT_MESSAGE)
                    .setAttribute("user", context.getUser())
                    .createResponse(UserModel.RequiredAction.VERIFY_EMAIL));
            return;
        }
        super.processAction(context);
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public int order() {
        return 10;
    }
}
