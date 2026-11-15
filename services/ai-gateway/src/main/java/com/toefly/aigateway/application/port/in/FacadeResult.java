package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.call.AiStamp;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ChatMessage;
import org.jspecify.annotations.Nullable;

/** The assistant message, unmasked, with its stamp and usage. */
public record FacadeResult(ChatMessage message, @Nullable String finishReason, TokenUsage usage, AiStamp aiStamp) {}
