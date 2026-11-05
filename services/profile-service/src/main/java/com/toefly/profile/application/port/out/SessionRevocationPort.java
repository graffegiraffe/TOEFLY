package com.toefly.profile.application.port.out;

import java.util.UUID;

/** Ends the web sessions of a user in api-gateway and rejects tokens issued before now (docs/10 §4.7). */
public interface SessionRevocationPort {

    void revokeSessions(UUID userId);
}
