package com.toefly.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toefly.content.application.port.in.BrowseCatalogUseCase.Filter;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Hit;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Row;
import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.application.port.out.EmbeddingPort;
import com.toefly.content.application.port.out.ItemSetStorePort;
import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.Source;
import com.toefly.content.domain.catalog.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogSearchTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final Filter ANY = new Filter(null, null, null, null, null, null, null, null, null);

    private final CatalogQueryPort query = mock(CatalogQueryPort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final CatalogQueryService service =
            new CatalogQueryService(query, mock(ItemSetStorePort.class), mock(SourceStorePort.class), embeddings);

    private static Row row(String topic) {
        UUID source = UUID.randomUUID();
        ItemSet set = new ItemSet(
                UUID.randomUUID(),
                null,
                1,
                TaskType.READ_DAILY_LIFE.section(),
                TaskType.READ_DAILY_LIFE,
                ExamFormat.TOEFL_2026,
                "B1",
                topic,
                null,
                List.of(),
                source,
                null,
                null,
                Provenance.EXTRACTED,
                ItemStatus.APPROVED,
                null,
                null,
                0,
                "SEED_LABEL",
                60,
                1,
                1,
                "{}",
                "UNCHECKED",
                "h",
                "",
                NOW,
                NOW,
                NOW);
        Source src = new Source(source, "s", "S", "P", 2026, null, ExamFormat.TOEFL_2026, "a", "{}");
        return new Row(set, src, 0);
    }

    @Test
    @DisplayName("search falls back to full text when the embedding model is unreachable")
    void fallsBackToFullText() {
        Row a = row("a");
        when(query.fullText(any(), any(), anyInt())).thenReturn(List.of(new Hit(a, 0.5)));
        when(embeddings.embed("q")).thenReturn(Optional.empty());

        var result = service.search("q", ANY, 10);

        assertThat(result.hybrid()).isFalse();
        assertThat(result.hits()).extracting(h -> h.row().itemSet().topic()).containsExactly("a");
    }

    @Test
    @DisplayName("hybrid search fuses both rankings with RRF, so a set found by both ranks first")
    void fusesRankings() {
        Row onlyText = row("text");
        Row both = row("both");
        Row onlyVector = row("vector");
        when(query.fullText(any(), any(), anyInt())).thenReturn(List.of(new Hit(onlyText, 1.0), new Hit(both, 0.9)));
        when(query.nearest(any(), any(), anyInt())).thenReturn(List.of(new Hit(onlyVector, 0.9), new Hit(both, 0.8)));
        when(embeddings.embed("q")).thenReturn(Optional.of(new float[] {0.1f}));

        var result = service.search("q", ANY, 10);

        assertThat(result.hybrid()).isTrue();
        var topics = result.hits().stream().map(h -> h.row().itemSet().topic()).toList();
        assertThat(topics.getFirst()).isEqualTo("both");
        assertThat(topics).containsExactlyInAnyOrder("both", "text", "vector");
    }

    @Test
    @DisplayName("a learner list is forced to approved sets and reports whether more rows follow")
    void listIsApprovedOnly() {
        when(query.list(any(), any(), any(), anyInt())).thenReturn(List.of(row("a"), row("b")));

        var page = service.list(ANY, false, null, null, 1);

        assertThat(page.rows()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
    }
}
