package com.toefly.content.application.port.out;

import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.Section;
import com.toefly.content.domain.catalog.Source;
import com.toefly.content.domain.catalog.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read side of the catalog: filtered lists and the two rankings of the hybrid search. */
public interface CatalogQueryPort {

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

    List<Row> list(Filter filter, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit);

    List<Hit> fullText(String query, Filter filter, int limit);

    List<Hit> nearest(float[] embedding, Filter filter, int limit);
}
