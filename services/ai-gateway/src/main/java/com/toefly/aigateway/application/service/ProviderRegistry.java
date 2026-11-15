package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.ChatProviderPort;
import com.toefly.aigateway.domain.routing.AiProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Chat adapters by provider; a route naming a provider without an adapter fails at that candidate. */
@Component
class ProviderRegistry {

    private final Map<AiProvider, ChatProviderPort> chat = new EnumMap<>(AiProvider.class);

    ProviderRegistry(List<ChatProviderPort> adapters) {
        adapters.forEach(adapter -> chat.put(adapter.provider(), adapter));
    }

    ChatProviderPort chat(AiProvider provider) {
        ChatProviderPort adapter = chat.get(provider);
        if (adapter == null) {
            throw new com.toefly.aigateway.application.port.out.ProviderFailure(
                    com.toefly.aigateway.application.port.out.ProviderFailure.Kind.REJECTED,
                    "no chat adapter for " + provider,
                    null,
                    null);
        }
        return adapter;
    }
}
