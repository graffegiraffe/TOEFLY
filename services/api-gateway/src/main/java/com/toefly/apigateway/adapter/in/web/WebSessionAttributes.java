package com.toefly.apigateway.adapter.in.web;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Attributes the BFF keeps in the web session next to the security context and the authorized client. */
final class WebSessionAttributes {

    static final String CSRF_TOKEN = "toefly.csrfToken";
    static final String RETURN_TO = "toefly.returnTo";

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebSessionAttributes() {}

    /** 128 random bits (docs/10-security-privacy.md §4.2). */
    static String newCsrfToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
