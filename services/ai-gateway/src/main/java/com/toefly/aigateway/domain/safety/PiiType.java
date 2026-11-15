package com.toefly.aigateway.domain.safety;

/** Kinds of personal data and their placeholders, e.g. {@code [EMAIL_1]} (§3.2). */
public enum PiiType {
    EMAIL,
    PHONE,
    URL,
    IBAN,
    CARD,
    DOC_ID,
    IP,
    HANDLE,
    DATE,
    PERSON,
    PLACE,
    ORG
}
