package com.toefly.tutoragent.application.port.out;

import java.util.UUID;

/** Rights of a learner from the entitlement_view projection (toefly.billing.entitlements-changed.v1). */
public interface EntitlementPort {

    boolean has(UUID userId, String entitlement);
}
