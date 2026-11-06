package com.toefly.content.adapter.out.seed;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Name-based UUID version 5 (RFC 9562), the same as Python's {@code uuid.uuid5} used by the extraction scripts. */
final class Uuids {

    /** {@code NAMESPACE_TOEFLY_SEED} of docs/08-content-pipeline.md section 4.6. */
    static final UUID NAMESPACE_TOEFLY_SEED = UUID.fromString("5b1c1c4e-6a8e-5a3b-9d5f-2f0c8d1a7e11");

    private Uuids() {}

    static UUID v5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16)
                    .putLong(namespace.getMostSignificantBits())
                    .putLong(namespace.getLeastSignificantBits())
                    .array());
            byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static UUID itemSet(String seedId) {
        return v5(NAMESPACE_TOEFLY_SEED, seedId);
    }

    static UUID item(String seedId, int ordinal) {
        return v5(NAMESPACE_TOEFLY_SEED, seedId + "#" + ordinal);
    }
}
