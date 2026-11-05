# TOEFLY Keycloak image: the toefly theme and the common-password blocklist of the password policy.
FROM quay.io/keycloak/keycloak:26.8.1
ENV KC_HEALTH_ENABLED=true KC_METRICS_ENABLED=true
COPY theme/toefly /opt/keycloak/themes/toefly
COPY password-blacklists /opt/keycloak/data/password-blacklists
