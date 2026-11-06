package com.toefly.content.application.service;

import com.toefly.content.application.port.in.BrowseCatalogUseCase;
import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.application.port.out.EmbeddingPort;
import com.toefly.content.application.port.out.ItemSetStorePort;
import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Source;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Browsing and search for learners (approved sets only) and for administrators (every status). */
@Service
public class CatalogQueryService implements BrowseCatalogUseCase {

    /** Reciprocal rank fusion constant (docs/08-content-pipeline.md §4.1). */
    private static final int RRF_K = 60;

    private final CatalogQueryPort query;
    private final ItemSetStorePort itemSets;
    private final SourceStorePort sources;
    private final EmbeddingPort embeddings;

    public CatalogQueryService(
            CatalogQueryPort query, ItemSetStorePort itemSets, SourceStorePort sources, EmbeddingPort embeddings) {
        this.query = query;
        this.itemSets = itemSets;
        this.sources = sources;
        this.embeddings = embeddings;
    }

    @Override
    public Page list(
            Filter filter, boolean admin, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit) {
        Filter effective = admin ? filter : filter.withStatus(ItemStatus.APPROVED);
        List<Row> rows = new ArrayList<>(query.list(effective, afterCreatedAt, afterId, limit + 1));
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more);
    }

    @Override
    public Optional<ItemSet> any(UUID id) {
        return itemSets.findById(id);
    }

    @Override
    public Source source(UUID id) {
        return sources.findById(id).orElseThrow();
    }

    @Override
    public List<Source> sources() {
        return sources.findAll();
    }

    @Override
    public SearchResult search(String q, Filter filter, int limit) {
        Filter effective = filter.withStatus(ItemStatus.APPROVED);
        int pool = Math.max(limit * 3, 30);
        List<Hit> text = query.fullText(q, effective, pool);
        Optional<float[]> vector = embeddings.embed(q);
        if (vector.isEmpty()) {
            return new SearchResult(false, text.stream().limit(limit).toList());
        }
        List<Hit> near = query.nearest(vector.get(), effective, pool);
        Map<UUID, Double> score = new HashMap<>();
        Map<UUID, Row> rows = new LinkedHashMap<>();
        fuse(text, score, rows);
        fuse(near, score, rows);
        List<Hit> fused = score.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Hit(rows.get(e.getKey()), e.getValue()))
                .toList();
        return new SearchResult(true, fused);
    }

    private static void fuse(List<Hit> ranking, Map<UUID, Double> score, Map<UUID, Row> rows) {
        for (int rank = 0; rank < ranking.size(); rank++) {
            Row row = ranking.get(rank).row();
            UUID id = row.itemSet().id();
            score.merge(id, 1.0 / (RRF_K + rank + 1), Double::sum);
            rows.putIfAbsent(id, row);
        }
    }
}
