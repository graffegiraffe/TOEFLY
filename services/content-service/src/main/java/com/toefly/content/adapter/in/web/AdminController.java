package com.toefly.content.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.content.adapter.in.web.api.AdminApi;
import com.toefly.content.adapter.in.web.dto.AdminItemSetPage;
import com.toefly.content.adapter.in.web.dto.AdminItemSetPatch;
import com.toefly.content.adapter.in.web.dto.AdminItemSetRow;
import com.toefly.content.adapter.in.web.dto.ExamFormat;
import com.toefly.content.adapter.in.web.dto.HideSourceResult;
import com.toefly.content.adapter.in.web.dto.ImportReport;
import com.toefly.content.adapter.in.web.dto.ImportReportSourcesInner;
import com.toefly.content.adapter.in.web.dto.ItemReport;
import com.toefly.content.adapter.in.web.dto.ItemReportPage;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.adapter.in.web.dto.ItemStatus;
import com.toefly.content.adapter.in.web.dto.PageInfo;
import com.toefly.content.adapter.in.web.dto.Provenance;
import com.toefly.content.adapter.in.web.dto.ReportStatus;
import com.toefly.content.adapter.in.web.dto.ResolveItemReportRequest;
import com.toefly.content.adapter.in.web.dto.Section;
import com.toefly.content.adapter.in.web.dto.StartImportRequest;
import com.toefly.content.adapter.in.web.dto.TaskType;
import com.toefly.content.application.port.in.ImportSeedUseCase;
import com.toefly.content.application.port.out.CatalogQueryPort.Filter;
import com.toefly.content.application.service.CatalogQueryService;
import com.toefly.content.application.service.ModerationService;
import com.toefly.content.domain.catalog.HideReason;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminController implements AdminApi {

    private final CatalogQueryService catalog;
    private final ModerationService moderation;
    private final ImportSeedUseCase imports;

    public AdminController(CatalogQueryService catalog, ModerationService moderation, ImportSeedUseCase imports) {
        this.catalog = catalog;
        this.moderation = moderation;
        this.imports = imports;
    }

    @Override
    public ResponseEntity<AdminItemSetPage> adminListItemSets(
            @Nullable Section section, @Nullable TaskType taskType, @Nullable ExamFormat examFormat,
            @Nullable Provenance provenance, @Nullable String cefr, @Nullable Boolean legacy,
            @Nullable String sourceCode, @Nullable ItemStatus status, @Nullable Boolean reported,
            @Nullable String cursor, Integer limit) {
        Filter base = CatalogController.filter(section, taskType, examFormat, provenance, cefr, legacy, sourceCode);
        Filter filter = new Filter(base.section(), base.taskType(), base.examFormat(), base.provenance(), base.cefr(),
                base.legacy(), base.sourceCode(),
                status == null ? null : com.toefly.content.domain.catalog.ItemStatus.valueOf(status.getValue()),
                reported);
        String fingerprint = Cursors.fingerprint(filter);
        Instant afterAt = null;
        UUID afterId = null;
        if (cursor != null) {
            List<String> keys = CursorCodec.decode(cursor, fingerprint);
            afterAt = Instant.parse(keys.get(0));
            afterId = UUID.fromString(keys.get(1));
        }
        var page = catalog.list(filter, true, afterAt, afterId, CursorCodec.limit(limit));
        PageInfo info = new PageInfo().hasMore(page.hasMore());
        if (page.hasMore()) {
            var last = page.rows().getLast().itemSet();
            info.nextCursor(CursorCodec.encode(fingerprint, List.of(last.createdAt().toString(), last.id().toString())));
        }
        return ResponseEntity.ok(new AdminItemSetPage()
                .items(page.rows().stream().map(CatalogMapper::adminRow).toList())
                .page(info));
    }

    @Override
    public ResponseEntity<ItemSet> adminGetItemSet(UUID itemSetId) {
        var set = catalog.any(itemSetId).orElseThrow(() -> new ModerationService.ItemSetNotFoundException(itemSetId));
        return ResponseEntity.ok(CatalogMapper.full(set, catalog.source(set.sourceId()), true));
    }

    @Override
    public ResponseEntity<AdminItemSetRow> adminPatchItemSet(UUID itemSetId, AdminItemSetPatch patch) {
        var target = com.toefly.content.domain.catalog.ItemStatus.valueOf(patch.getStatus().getValue());
        var next = moderation.changeStatus(itemSetId, target, HideReason.ADMIN);
        return ResponseEntity.ok(CatalogMapper.adminRow(new com.toefly.content.application.port.out.CatalogQueryPort.Row(
                next, catalog.source(next.sourceId()), 0)));
    }

    @Override
    public ResponseEntity<HideSourceResult> adminHideSource(String sourceCode) {
        return ResponseEntity.ok(new HideSourceResult().sourceCode(sourceCode).hidden(moderation.hideSource(sourceCode)));
    }

    @Override
    public ResponseEntity<ItemReportPage> adminListItemReports(@Nullable ReportStatus status, Integer limit) {
        var reports = moderation.reports(
                status == null ? null : com.toefly.content.domain.report.ReportStatus.valueOf(status.getValue()),
                CursorCodec.limit(limit));
        return ResponseEntity.ok(new ItemReportPage().items(reports.stream().map(AdminController::report).toList()));
    }

    @Override
    public ResponseEntity<ItemReport> adminResolveItemReport(UUID reportId, ResolveItemReportRequest request) {
        UUID admin = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        var closed = moderation.resolve(reportId, admin,
                com.toefly.content.domain.report.ReportStatus.valueOf(request.getOutcome().getValue()),
                Boolean.TRUE.equals(request.getHideItemSet()));
        return ResponseEntity.ok(report(closed));
    }

    @Override
    public ResponseEntity<ImportReport> adminStartImport(StartImportRequest request) {
        var report = imports.run(Optional.ofNullable(request.getSourceCode()), Boolean.TRUE.equals(request.getDryRun()));
        return ResponseEntity.accepted()
                .body(new ImportReport()
                        .dryRun(report.dryRun())
                        .sources(report.sources().stream()
                                .map(s -> new ImportReportSourcesInner()
                                        .sourceCode(s.sourceCode())
                                        .status(s.status())
                                        .created(s.created())
                                        .updated(s.updated())
                                        .unchanged(s.unchanged())
                                        .hidden(s.hidden())
                                        .error(s.error()))
                                .toList()));
    }

    static ItemReport report(com.toefly.content.domain.report.ItemReport r) {
        return new ItemReport()
                .id(r.id())
                .itemSetId(r.itemSetId())
                .itemOrdinal(r.itemOrdinal())
                .reason(com.toefly.content.adapter.in.web.dto.ReportReason.fromValue(r.reason().name()))
                .comment(r.comment())
                .status(ReportStatus.fromValue(r.status().name()))
                .createdAt(r.createdAt().atOffset(ZoneOffset.UTC))
                .resolvedAt(r.resolvedAt() == null ? null : r.resolvedAt().atOffset(ZoneOffset.UTC));
    }
}
