package com.toefly.common.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Salted SHA-256 of a user id for the {@code toefly.userIdHash} log field; logs never carry the id itself. */
public class UserIdHasher {

    private final byte[] salt;

    public UserIdHasher(String salt) {
        if (salt.isBlank()) {
            throw new IllegalArgumentException("toefly.observability.user-id-salt must be set");
        }
        this.salt = salt.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return HexFormat.of().formatHex(digest.digest(userId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
