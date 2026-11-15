package com.toefly.aigateway.domain.prompt;

/** Life cycle of a prompt version (§2.9): DRAFT → CANDIDATE → ACTIVE → ARCHIVED. */
public enum PromptStatus {
    DRAFT,
    CANDIDATE,
    ACTIVE,
    ARCHIVED
}
