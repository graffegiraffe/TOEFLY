package com.toefly.aigateway.application.service;

import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Messages ready for a provider, the masked variables for the cache key and the journal, the input verdict. */
record PreparedPrompt(
        List<ChatMessage> messages,
        Map<String, Object> maskedVariables,
        @Nullable GuardAssessment inputGuard) {}
