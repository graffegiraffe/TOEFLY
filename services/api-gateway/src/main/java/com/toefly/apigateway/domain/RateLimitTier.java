package com.toefly.apigateway.domain;

/** Rate limit tiers of docs/05-api-contracts.md §2.10, from the caller's plan and role. */
public enum RateLimitTier {
    ANONYMOUS,
    STUDENT_FREE,
    STUDENT_PRO,
    TUTOR,
    ADMIN
}
