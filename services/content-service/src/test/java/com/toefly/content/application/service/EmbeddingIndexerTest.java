package com.toefly.content.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toefly.content.application.port.out.EmbeddingPort;
import com.toefly.content.application.port.out.EmbeddingStorePort;
import com.toefly.content.application.port.out.EmbeddingStorePort.Pending;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingIndexerTest {

    private final EmbeddingStorePort store = mock(EmbeddingStorePort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final EmbeddingIndexer indexer = new EmbeddingIndexer(store, embeddings);

    @Test
    @DisplayName("new sets are embedded and stored, long texts are cut")
    void indexesPending() {
        UUID id = UUID.randomUUID();
        when(store.pending(anyInt())).thenReturn(List.of(new Pending(id, "x".repeat(9000))));
        float[] vector = {0.1f};
        when(embeddings.embed(anyString())).thenReturn(Optional.of(vector));

        indexer.indexPending();

        verify(store)
                .save(
                        org.mockito.ArgumentMatchers.eq(id),
                        org.mockito.ArgumentMatchers.eq(0),
                        org.mockito.ArgumentMatchers.argThat(text -> text.length() == 6000),
                        org.mockito.ArgumentMatchers.same(vector));
    }

    @Test
    @DisplayName("an unreachable model leaves everything for the next run")
    void retriesLater() {
        when(store.pending(anyInt())).thenReturn(List.of(new Pending(UUID.randomUUID(), "text")));
        when(embeddings.embed(anyString())).thenReturn(Optional.empty());

        indexer.indexPending();

        verify(store, never()).save(any(), anyInt(), anyString(), any());
    }
}
