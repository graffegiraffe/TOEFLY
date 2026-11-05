package com.toefly.keycloak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Sign-up with an email that already has an account (AC-01.01.3): the owner of the address gets "someone tried to
 * sign up with your email" without a confirmation link, at most once in 15 minutes, while the form shows the same
 * neutral message as for a new address (theme message {@code emailExistsMessage}). Runs before the built-in user
 * creation in the registration form.
 */
public class SignUpAttemptNoticeFormAction implements FormAction, FormActionFactory {

    public static final String ID = "toefly-sign-up-attempt-notice";
    static final long NOTICE_INTERVAL_SECONDS = 900;
    private static final Logger log = Logger.getLogger(SignUpAttemptNoticeFormAction.class);

    @Override
    public void validate(ValidationContext context) {
        String email = context.getHttpRequest().getDecodedFormParameters().getFirst(UserModel.EMAIL);
        if (email == null || email.isBlank()) {
            context.success();
            return;
        }
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        UserModel owner = session.users().getUserByEmail(realm, email.strip());
        if (owner != null && session.singleUseObjects().putIfAbsent("toefly-sign-up-attempt:" + owner.getId(),
                NOTICE_INTERVAL_SECONDS)) {
            try {
                session.getProvider(EmailTemplateProvider.class)
                        .setRealm(realm)
                        .setUser(owner)
                        .send("signUpAttemptSubject", "sign-up-attempt.ftl", attributes(realm));
            } catch (EmailException e) {
                log.warn("Could not send the sign-up attempt notice", e);
            }
        }
        // The built-in user creation reports the taken email with the neutral theme message.
        context.success();
    }

    /** Keycloak adds its own entries (locale, links) to the template attributes, so the map must be mutable. */
    static Map<String, Object> attributes(RealmModel realm) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realmName", realm.getDisplayName());
        return attributes;
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {}

    @Override
    public void success(FormContext context) {}

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public String getDisplayType() {
        return "TOEFLY sign-up attempt notice";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    private static final Requirement[] REQUIREMENTS = {Requirement.REQUIRED, Requirement.DISABLED};

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Emails the owner of an already registered address instead of revealing that it is taken.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return ID;
    }
}
