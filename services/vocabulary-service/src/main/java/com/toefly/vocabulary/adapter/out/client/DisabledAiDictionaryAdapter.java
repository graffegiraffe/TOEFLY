package com.toefly.vocabulary.adapter.out.client;

import com.toefly.vocabulary.application.port.out.AiDictionaryPort;
import com.toefly.vocabulary.application.port.out.AiUnavailableException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code toefly.vocabulary.ai.enabled=false}: words are saved without AI data; key words are unavailable. */
public class DisabledAiDictionaryAdapter implements AiDictionaryPort {

    @Override
    public WordCard wordCard(String term, @Nullable String context, UUID userId) {
        throw new AiUnavailableException("AI dictionary is disabled");
    }

    @Override
    public KeyWords keyWords(String text, int max, UUID userId) {
        throw new AiUnavailableException("AI dictionary is disabled");
    }
}
