package com.toefly.aigateway.adapter.out.valkey;

import com.toefly.aigateway.application.port.out.ConversationMaskingPort;
import com.toefly.aigateway.domain.safety.MaskingSession;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Masking mapping of a chat conversation in Valkey {@code pii:{conversationId}} (§3.2): AES-GCM with a key from a
 * Sealed Secret, a fresh IV per write, TTL 24 hours.
 */
public class EncryptedConversationMasking implements ConversationMaskingPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final StringRedisTemplate valkey;
    private final SecretKeySpec key;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public EncryptedConversationMasking(StringRedisTemplate valkey, byte[] key, Duration ttl) {
        if (key.length != 32) {
            throw new IllegalArgumentException("the masking key must be 32 bytes (AES-256)");
        }
        this.valkey = valkey;
        this.key = new SecretKeySpec(key, "AES");
        this.ttl = ttl;
    }

    @Override
    public MaskingSession load(String conversationId) {
        String stored = valkey.opsForValue().get(redisKey(conversationId));
        if (stored == null) {
            return new MaskingSession();
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] sealed = new byte[buffer.remaining()];
            buffer.get(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(conversationId.getBytes(StandardCharsets.UTF_8));
            String json = new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
            return MaskingSession.restore(JSON.readValue(json, new TypeReference<List<MaskingSession.Entry>>() {}));
        } catch (GeneralSecurityException | RuntimeException unreadable) {
            return new MaskingSession();
        }
    }

    @Override
    public void save(String conversationId, MaskingSession session) {
        if (session.isEmpty()) {
            return;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(conversationId.getBytes(StandardCharsets.UTF_8));
            byte[] sealed =
                    cipher.doFinal(JSON.writeValueAsString(session.entries()).getBytes(StandardCharsets.UTF_8));
            byte[] all = ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv)
                    .put(sealed)
                    .array();
            valkey.opsForValue()
                    .set(redisKey(conversationId), Base64.getEncoder().encodeToString(all), ttl);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot seal the masking session", e);
        }
    }

    private static String redisKey(String conversationId) {
        return "pii:" + conversationId;
    }
}
