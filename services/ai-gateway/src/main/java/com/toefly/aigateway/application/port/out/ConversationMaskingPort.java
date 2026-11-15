package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.safety.MaskingSession;

/** Masking mapping of a chat conversation between turns (Valkey {@code pii:{conversationId}}, AES-GCM, TTL 24 h). */
public interface ConversationMaskingPort {

    MaskingSession load(String conversationId);

    void save(String conversationId, MaskingSession session);
}
