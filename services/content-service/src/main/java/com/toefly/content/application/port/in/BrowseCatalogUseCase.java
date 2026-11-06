package com.toefly.content.application.port.in;

import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.Section;
import com.toefly.content.domain.catalog.Source;
import com.toefly.content.domain.catalog.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Browsing and searching the item bank, for learners (approved sets only) and for administrators. */
public interface BrowseCatalogUseCase {

    record Filter(
            @Nullable Section section,
            @Nullable TaskType taskType,
            @Nullable ExamFormat examFormat,
            @Nullable Provenance provenance,
            @Nullable String cefr,
            @Nullable Boolean legacy,
            @Nullable String sourceCode,
            @Nullable ItemStatus status,
            @Nullable Boolean reported) {

        public Filter withStatus(ItemStatus forced) {
            return new Filter(section, taskType, examFormat, provenance, cefr, legacy, sourceCode, forced, reported);
        }
    }

    record Row(ItemSet itemSet, Source source, int openReports) {}

    record Hit(Row row, double score) {}

    record Page(List<Row> rows, boolean hasMore) {}

    record SearchResult(boolean hybrid, List<Hit> hits) {}

    Page list(Filter filter, boolean admin, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit);

    SearchResult search(String q, Filter filter, int limit);

    Optional<ItemSet> any(UUID id);

    Source source(UUID id);

    List<Source> sources();
}
