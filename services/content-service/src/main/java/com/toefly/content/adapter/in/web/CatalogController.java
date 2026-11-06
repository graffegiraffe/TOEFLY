package com.toefly.content.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.content.adapter.in.web.api.ItemSetsApi;
import com.toefly.content.adapter.in.web.api.ReportsApi;
import com.toefly.content.adapter.in.web.api.SourcesApi;
import com.toefly.content.adapter.in.web.dto.CreateItemReportRequest;
import com.toefly.content.adapter.in.web.dto.ExamFormat;
import com.toefly.content.adapter.in.web.dto.ItemReport;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.adapter.in.web.dto.ItemSetSearchResult;
import com.toefly.content.adapter.in.web.dto.ItemSetSummaryPage;
import com.toefly.content.adapter.in.web.dto.PageInfo;
import com.toefly.content.adapter.in.web.dto.Provenance;
import com.toefly.content.adapter.in.web.dto.SearchHit;
import com.toefly.content.adapter.in.web.dto.Section;
import com.toefly.content.adapter.in.web.dto.SourceList;
import com.toefly.content.adapter.in.web.dto.TaskType;
import com.toefly.content.application.port.out.CatalogQueryPort.Filter;
import com.toefly.content.application.service.CatalogQueryService;
import com.toefly.content.application.service.ModerationService;
import com.toefly.content.domain.report.ReportReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController implements ItemSetsApi, SourcesApi, ReportsApi {

    private final CatalogQueryService catalog;
    private final ModerationService moderation;

    public CatalogController(CatalogQueryService catalog, ModerationService moderation) {
        this.catalog = catalog;
        this.moderation = moderation;
    }

    @Override
    public ResponseEntity<ItemSetSummaryPage> listItemSets(
            @Nullable Section section, @Nullable TaskType taskType, @Nullable ExamFormat examFormat,
            @Nullable Provenance provenance, @Nullable String cefr, @Nullable Boolean legacy,
            @Nullable String sourceCode, @Nullable String cursor, Integer limit) {
        Filter filter = filter(section, taskType, examFormat, provenance, cefr, legacy, sourceCode);
        return ResponseEntity.ok(page(filter, false, cursor, limit));
    }

    private ItemSetSummaryPage page(Filter filter, boolean admin, @Nullable String cursor, int limit) {
        String fingerprint = Cursors.fingerprint(filter);
        Instant afterAt = null;
        UUID afterId = null;
        if (cursor != null) {
            List<String> keys = CursorCodec.decode(cursor, fingerprint);
            afterAt = Instant.parse(keys.get(0));
            afterId = UUID.fromString(keys.get(1));
        }
        var page = catalog.list(filter, admin, afterAt, afterId, CursorCodec.limit(limit));
        PageInfo info = new PageInfo().hasMore(page.hasMore());
        if (page.hasMore()) {
            var last = page.rows().getLast().itemSet();
            info.nextCursor(CursorCodec.encode(fingerprint, List.of(last.createdAt().toString(), last.id().toString())));
        }
        return new ItemSetSummaryPage().items(page.rows().stream().map(CatalogMapper::summary).toList()).page(info);
    }

    @Override
    public ResponseEntity<ItemSetSearchResult> searchItemSets(
            String q, @Nullable Section section, @Nullable TaskType taskType, @Nullable ExamFormat examFormat,
            @Nullable Provenance provenance, @Nullable String cefr, @Nullable Boolean legacy,
            @Nullable String sourceCode, Integer limit) {
        var result = catalog.search(
                q, filter(section, taskType, examFormat, provenance, cefr, legacy, sourceCode), CursorCodec.limit(limit));
        return ResponseEntity.ok(new ItemSetSearchResult()
                .mode(result.hybrid() ? ItemSetSearchResult.ModeEnum.HYBRID : ItemSetSearchResult.ModeEnum.FULL_TEXT)
                .hits(result.hits().stream()
                        .map(h -> new SearchHit()
                                .itemSet(CatalogMapper.summary(h.row()))
                                .score(BigDecimal.valueOf(h.score())))
                        .toList()));
    }

    @Override
    public ResponseEntity<ItemSet> getItemSet(UUID itemSetId) {
        var set = catalog.any(itemSetId).orElseThrow(() -> new ModerationService.ItemSetNotFoundException(itemSetId));
        if (!set.available()) {
            throw new ItemSetUnavailableException();
        }
        return ResponseEntity.ok(CatalogMapper.full(set, catalog.source(set.sourceId()), false));
    }

    @Override
    public ResponseEntity<SourceList> listSources() {
        return ResponseEntity.ok(new SourceList()
                .items(catalog.sources().stream().map(CatalogMapper::source).toList()));
    }

    @Override
    public ResponseEntity<ItemReport> createItemReport(CreateItemReportRequest request, @Nullable String idempotencyKey) {
        UUID reporter = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        var report = moderation.report(reporter, request.getItemSetId(), request.getItemOrdinal(),
                ReportReason.valueOf(request.getReason().getValue()), request.getComment());
        return ResponseEntity.status(201).body(AdminController.report(report));
    }

    static Filter filter(@Nullable Section section, @Nullable TaskType taskType, @Nullable ExamFormat examFormat,
            @Nullable Provenance provenance, @Nullable String cefr, @Nullable Boolean legacy, @Nullable String sourceCode) {
        return new Filter(
                section == null ? null : com.toefly.content.domain.catalog.Section.valueOf(section.getValue()),
                taskType == null ? null : com.toefly.content.domain.catalog.TaskType.valueOf(taskType.getValue()),
                examFormat == null ? null : com.toefly.content.domain.catalog.ExamFormat.valueOf(examFormat.getValue()),
                provenance == null ? null : com.toefly.content.domain.catalog.Provenance.valueOf(provenance.getValue()),
                cefr, legacy, sourceCode, null, null);
    }
}
