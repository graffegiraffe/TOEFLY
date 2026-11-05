package com.toefly.keycloak;

import java.util.Map;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;

/** {@link HourlyLimit.Store} over the single-use object store of the session. */
final class SingleUseObjectStore implements HourlyLimit.Store {

    private final SingleUseObjectProvider objects;

    SingleUseObjectStore(KeycloakSession session) {
        this.objects = session.singleUseObjects();
    }

    @Override
    public Map<String, String> get(String key) {
        return objects.get(key);
    }

    @Override
    public boolean putIfAbsent(String key, long lifespanSeconds) {
        return objects.putIfAbsent(key, lifespanSeconds);
    }

    @Override
    public boolean replace(String key, Map<String, String> notes) {
        return objects.replace(key, notes);
    }
}
