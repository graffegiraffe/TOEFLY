package com.toefly.billing.domain.entitlement;

/**
 * Where a right comes from. The Free base set has source {@code SUBSCRIPTION} without a source id: the plan
 * {@code STUDENT_FREE} has no subscription row (docs/03 §6.13).
 */
public enum EntitlementSource {
    SUBSCRIPTION,
    TRIAL,
    SEAT,
    MANUAL
}
