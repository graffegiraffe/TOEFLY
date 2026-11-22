package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.entitlement.EntitlementSet;
import java.time.Instant;

/** A snapshot of rights with its version ({@code ETag}). */
public record EntitlementView(EntitlementSet set, long version, Instant changedAt) {}
