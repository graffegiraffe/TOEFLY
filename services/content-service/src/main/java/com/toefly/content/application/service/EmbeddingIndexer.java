package com.toefly.content.application.service;

import com.toefly.content.application.port.out.EmbeddingPort;
import com.toefly.content.application.port.out.EmbeddingStorePort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Embeds new item sets in the background; a no-op while the embedding model is unreachable. */
@Component
public class EmbeddingIndexer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexer.class);
    private static final int MAX_CHARS = 6000;

    private final EmbeddingStorePort store;
    private final EmbeddingPort embeddings;

    public EmbeddingIndexer(EmbeddingStorePort store, EmbeddingPort embeddings) {
        this.store = store;
        this.embeddings = embeddings;
    }

    @Scheduled(fixedDelayString = "${toefly.content.embeddings.interval:PT1M}", initialDelayString = "PT30S")
    public void indexPending() {
        for (var pending : store.pending(20)) {
            String text = pending.text().length() > MAX_CHARS ? pending.text().substring(0, MAX_CHARS) : pending.text();
            Optional<float[]> vector = embeddings.embed(text);
            if (vector.isEmpty()) {
                log.debug("Embedding model unreachable; will retry");
                return;
            }
            store.save(pending.itemSetId(), 0, text, vector.get());
        }
    }
}
