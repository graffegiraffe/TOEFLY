package com.toefly.content.application.service;

import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.application.port.out.CatalogQueryPort.Filter;
import com.toefly.content.application.port.out.CatalogQueryPort.Hit;
import com.toefly.content.application.port.out.CatalogQueryPort.Row;
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
public class CatalogQueryService {

    /** Reciprocal rank fusion constant (docs/08-content-pipeline.md §4.1). */
    private static final int RRF_K = 60;

    public record Page(List<Row> rows, boolean hasMore) {}

    public record SearchResult(boolean hybrid, List<Hit> hits) {}

    private final CatalogQueryPort query;
    private final ItemSetStorePort itemSets;
    private final SourceStorePort sources;
    private final EmbeddingPort embeddings;

    public CatalogQueryService(CatalogQueryPort query, ItemSetStorePort itemSets, SourceStorePort sources,
            EmbeddingPort embeddings) {
        this.query = query;
        this.itemSets = itemSets;
        this.sources = sources;
        this.embeddings = embeddings;
    }

    public Page list(Filter filter, boolean admin, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit) {
        Filter effective = admin ? filter : filter.withStatus(ItemStatus.APPROVED);
        List<Row> rows = new ArrayList<>(query.list(effective, afterCreatedAt, afterId, limit + 1));
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more);
    }

    /** Learner view: only approved sets exist. */
    public Optional<ItemSet> approved(UUID id) {
        return itemSets.findById(id).filter(ItemSet::available);
    }

    public Optional<ItemSet> any(UUID id) {
        return itemSets.findById(id);
    }

    public Source source(UUID id) {
        return sources.findById(id).orElseThrow();
    }

    public List<Source> sources() {
        return sources.findAll();
    }

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
