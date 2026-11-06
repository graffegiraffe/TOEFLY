package com.toefly.content.application.service;

import com.toefly.content.application.port.in.ModerateCatalogUseCase;
import com.toefly.content.application.port.out.ContentEventPublisherPort;
import com.toefly.content.application.port.out.ItemSetStorePort;
import com.toefly.content.application.port.out.ReportStorePort;
import com.toefly.content.domain.catalog.HideReason;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemSetNotFoundException;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.SourceNotFoundException;
import com.toefly.content.domain.report.DuplicateReportException;
import com.toefly.content.domain.report.ItemReport;
import com.toefly.content.domain.report.ReportNotFoundException;
import com.toefly.content.domain.report.ReportReason;
import com.toefly.content.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Status changes by administrators, takedown of a whole source and the complaint queue. */
@Service
public class ModerationService implements ModerateCatalogUseCase {

    private final ItemSetStorePort itemSets;
    private final ReportStorePort reports;
    private final ContentEventPublisherPort events;
    private final com.toefly.content.application.port.out.SourceStorePort sources;
    private final Clock clock;

    public ModerationService(
            ItemSetStorePort itemSets,
            ReportStorePort reports,
            ContentEventPublisherPort events,
            com.toefly.content.application.port.out.SourceStorePort sources,
            Clock clock) {
        this.itemSets = itemSets;
        this.reports = reports;
        this.events = events;
        this.sources = sources;
        this.clock = clock;
    }

    /** Moves a set to HIDDEN, APPROVED or REJECTED and publishes the matching event (INV-ITM-02). */
    @Override
    @Transactional
    public ItemSet changeStatus(UUID id, ItemStatus target, HideReason reasonIfHidden) {
        ItemSet current = itemSets.findById(id).orElseThrow(() -> new ItemSetNotFoundException(id));
        Instant now = clock.instant();
        ItemSet next = switch (target) {
            case HIDDEN -> current.hide(reasonIfHidden, now);
            case APPROVED -> current.approve(now);
            case REJECTED -> current.reject(now);
            case IN_REVIEW -> current.sendToReview(now);
            case DRAFT -> throw new IllegalArgumentException("DRAFT cannot be set by hand");
        };
        itemSets.saveStatus(next);
        if (target == ItemStatus.APPROVED) {
            events.itemSetPublished(next, itemSets.items(id), now);
        } else if (target == ItemStatus.HIDDEN && current.available()) {
            events.itemSetHidden(next, now);
        }
        return next;
    }

    /** Takedown on request of a rights holder (docs/08-content-pipeline.md §3.2). */
    @Override
    @Transactional
    public int hideSource(String sourceCode) {
        var source = sources.findByCode(sourceCode).orElseThrow(() -> new SourceNotFoundException(sourceCode));
        int hidden = 0;
        for (UUID id : itemSets.idsOfSource(source.id())) {
            ItemSet set = itemSets.findById(id).orElseThrow();
            if (set.status() == ItemStatus.APPROVED || set.status() == ItemStatus.IN_REVIEW) {
                changeStatus(id, ItemStatus.HIDDEN, HideReason.SOURCE_TAKEDOWN);
                hidden++;
            }
        }
        return hidden;
    }

    @Override
    @Transactional
    public ItemReport report(
            UUID reporter, UUID itemSetId, @Nullable Integer ordinal, ReportReason reason, @Nullable String comment) {
        ItemSet set = itemSets.findById(itemSetId).orElseThrow(() -> new ItemSetNotFoundException(itemSetId));
        if (ordinal != null && ordinal > set.itemCount()) {
            throw new IllegalArgumentException("item " + ordinal + " does not exist");
        }
        ItemReport report =
                ItemReport.open(UUID.randomUUID(), itemSetId, ordinal, reporter, reason, comment, clock.instant());
        if (!reports.insert(report)) {
            throw new DuplicateReportException();
        }
        return report;
    }

    @Override
    @Transactional
    public ItemReport resolve(UUID reportId, UUID admin, ReportStatus outcome, boolean hideItemSet) {
        ItemReport report = reports.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));
        ItemReport closed = report.close(outcome, admin, clock.instant());
        reports.update(closed);
        if (outcome == ReportStatus.RESOLVED && hideItemSet) {
            ItemSet set = itemSets.findById(report.itemSetId()).orElseThrow();
            if (set.available()) {
                changeStatus(set.id(), ItemStatus.HIDDEN, HideReason.REPORT);
            }
        }
        return closed;
    }

    @Override
    public List<ItemReport> reports(@Nullable ReportStatus status, int limit) {
        return new ArrayList<>(reports.list(status, limit));
    }
}
