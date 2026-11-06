package com.toefly.content.application.port.out;

import com.toefly.content.application.port.in.BrowseCatalogUseCase.Filter;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Hit;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Row;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read side of the catalog: filtered lists and the two rankings of the hybrid search. */
public interface CatalogQueryPort {

    List<Row> list(Filter filter, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit);

    List<Hit> fullText(String query, Filter filter, int limit);

    List<Hit> nearest(float[] embedding, Filter filter, int limit);
}
