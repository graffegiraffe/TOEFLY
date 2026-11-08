package com.toefly.content.application.service;

import com.toefly.content.application.port.in.ImportReport;
import com.toefly.content.application.port.out.ContentEventPublisherPort;
import com.toefly.content.application.port.out.ExtractionStorePort;
import com.toefly.content.application.port.out.ItemSetStorePort;
import com.toefly.content.application.port.out.ItemSetStorePort.ItemRow;
import com.toefly.content.application.port.out.SeedImportStorePort;
import com.toefly.content.application.port.out.SeedLocationPort;
import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.application.port.out.UnitStorePort;
import com.toefly.content.application.seed.SeedItemSet;
import com.toefly.content.application.seed.SeedManifest;
import com.toefly.content.application.seed.SeedSourceInfo;
import com.toefly.content.application.seed.SeedUnit;
import com.toefly.content.domain.catalog.Difficulty;
import com.toefly.content.domain.catalog.HideReason;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.catalog.Source;
import com.toefly.content.domain.catalog.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent import of one source bundle; the rules are the table of docs/08-content-pipeline.md §4.10. */
@Component
public class SourceImporter {

    private final SeedLocationPort seed;
    private final SourceStorePort sources;
    private final ItemSetStorePort itemSets;
    private final SeedImportStorePort imports;
    private final ContentEventPublisherPort events;
    private final UnitStorePort units;
    private final ExtractionStorePort extractions;
    private final Clock clock;

    public SourceImporter(
            SeedLocationPort seed,
            SourceStorePort sources,
            ItemSetStorePort itemSets,
            SeedImportStorePort imports,
            ContentEventPublisherPort events,
            UnitStorePort units,
            ExtractionStorePort extractions,
            Clock clock) {
        this.seed = seed;
        this.sources = sources;
        this.itemSets = itemSets;
        this.imports = imports;
        this.events = events;
        this.units = units;
        this.extractions = extractions;
        this.clock = clock;
    }

    @Transactional
    public ImportReport.SourceResult importSource(SeedManifest.SourceBundle bundle, boolean dryRun) {
        if (!imports.tryLock()) {
            throw new IllegalStateException("another import is running");
        }
        String code = bundle.sourceCode();
        if (imports.bundleSha(code).filter(bundle.bundleSha256()::equals).isPresent()) {
            return ImportReport.SourceResult.skipped(code);
        }
        Instant started = clock.instant();
        SeedSourceInfo info = seed.source(code);
        Source source = dryRun ? stub(info) : sources.upsert(toSource(info));

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        Set<UUID> present = new HashSet<>();
        for (SeedManifest.FileRef file : bundle.files()) {
            if (!"ITEM_SET".equals(file.kind())) {
                SeedUnit unit = seed.unit(file);
                present.add(unit.id());
                if (!dryRun) {
                    extractions.save(unit.id(), unit.seedId(), unit.extraction());
                }
                Optional<String> stored = units.contentHash(unit.kind(), unit.id());
                if (stored.isEmpty()) {
                    created++;
                    if (!dryRun) {
                        units.insert(unit, source.id(), unit.reviewed() ? "APPROVED" : "IN_REVIEW");
                    }
                } else if (stored.get().equals(unit.contentHash())) {
                    unchanged++;
                } else {
                    updated++;
                    if (!dryRun) {
                        units.updateContent(unit);
                    }
                }
                continue;
            }
            SeedItemSet item = seed.itemSet(file);
            present.add(item.id());
            if (!dryRun) {
                extractions.save(item.id(), item.seedId(), item.extraction());
            }
            Optional<ItemSet> existing = itemSets.findById(item.id());
            if (existing.isEmpty()) {
                created++;
                if (!dryRun) {
                    insert(source, item);
                }
            } else if (existing.get().contentHash().equals(item.contentHash())) {
                unchanged++;
            } else {
                updated++;
                if (!dryRun) {
                    update(existing.get(), item);
                }
            }
        }
        int hidden = dryRun ? 0 : hideRemoved(source, present);
        Instant finished = clock.instant();
        if (!dryRun) {
            imports.record(
                    code,
                    bundle.bundleSha256(),
                    "DONE",
                    Map.of("created", created, "updated", updated, "unchanged", unchanged, "hidden", hidden),
                    "{}",
                    started,
                    finished);
        }
        return new ImportReport.SourceResult(code, "DONE", created, updated, unchanged, hidden, null);
    }

    private void insert(Source source, SeedItemSet seedItem) {
        Instant now = clock.instant();
        ItemStatus status = seedItem.needsReview() && !seedItem.reviewed() ? ItemStatus.IN_REVIEW : ItemStatus.APPROVED;
        ItemSet itemSet = build(source, seedItem, status, 1, now, now);
        List<ItemRow> rows = rows(source, seedItem);
        itemSets.insert(itemSet, rows);
        if (itemSet.available()) {
            events.itemSetPublished(itemSet, rows, now);
        }
    }

    private void update(ItemSet existing, SeedItemSet seedItem) {
        Instant now = clock.instant();
        Source source = sources.findById(existing.sourceId()).orElseThrow();
        // status, hidden reason and review fields belong to the administrator and are not touched
        ItemSet changed = build(source, seedItem, existing.status(), existing.version() + 1, existing.createdAt(), now);
        ItemSet merged = withAdminFields(changed, existing);
        List<ItemRow> rows = rows(source, seedItem);
        itemSets.updateContent(merged, rows, null);
        if (merged.available()) {
            events.itemSetPublished(merged, rows, now);
        }
    }

    private int hideRemoved(Source source, Set<UUID> present) {
        int hidden = 0;
        for (UUID id : itemSets.seedIdsOfSource(source.id())) {
            if (present.contains(id)) {
                continue;
            }
            ItemSet itemSet = itemSets.findById(id).orElseThrow();
            if (itemSet.status() == ItemStatus.HIDDEN || itemSet.status() == ItemStatus.REJECTED) {
                continue;
            }
            Instant now = clock.instant();
            boolean wasApproved = itemSet.available();
            ItemSet next = itemSet.status() == ItemStatus.APPROVED
                    ? itemSet.hide(HideReason.SEED_REMOVED, now)
                    : itemSet.reject(now);
            itemSets.saveStatus(next);
            if (wasApproved) {
                events.itemSetHidden(next, now);
            }
            hidden++;
        }
        return hidden;
    }

    private ItemSet build(
            Source source, SeedItemSet s, ItemStatus status, int version, Instant createdAt, Instant now) {
        TaskType type = s.taskType();
        int count = s.items().size();
        int norm = type.etsNormSeconds(count, s.audioSeconds());
        double b = s.items().stream()
                .mapToDouble(i -> Difficulty.initialB(s.cefr(), i.subskill(), type.legacy()))
                .average()
                .orElse(0);
        return new ItemSet(
                s.id(),
                s.seedId(),
                version,
                type.section(),
                type,
                type.examFormat(),
                s.cefr(),
                s.topic(),
                s.domain(),
                s.skills(),
                source.id(),
                s.sourcePage(),
                s.sourceItemNo(),
                s.provenance(),
                status,
                null,
                null,
                Math.round(b * 100) / 100.0,
                Difficulty.SEED_LABEL,
                norm,
                TaskType.estimatedMinutes(norm),
                count,
                s.payloadJson(),
                "UNCHECKED",
                s.contentHash(),
                s.searchText(),
                status == ItemStatus.APPROVED ? now : null,
                createdAt,
                now);
    }

    private static ItemSet withAdminFields(ItemSet changed, ItemSet existing) {
        return new ItemSet(
                changed.id(),
                changed.seedId(),
                changed.version(),
                changed.section(),
                changed.taskType(),
                changed.examFormat(),
                changed.cefr(),
                changed.topic(),
                changed.domain(),
                changed.skills(),
                changed.sourceId(),
                changed.sourcePage(),
                changed.sourceItemNo(),
                changed.provenance(),
                existing.status(),
                existing.hiddenReason(),
                existing.ownerTutorId(),
                existing.bMean(),
                existing.bBasis(),
                changed.etsNormSeconds(),
                changed.estimatedMinutes(),
                changed.itemCount(),
                changed.payload(),
                existing.audioState(),
                changed.contentHash(),
                changed.searchText(),
                existing.publishedAt(),
                existing.createdAt(),
                changed.updatedAt());
    }

    private static List<ItemRow> rows(Source source, SeedItemSet s) {
        return s.items().stream()
                .map(i -> new ItemRow(
                        i.id(),
                        i.ordinal(),
                        i.subskill(),
                        i.maxScore(),
                        Difficulty.initialB(s.cefr(), i.subskill(), source.legacy()),
                        Difficulty.SEED_LABEL))
                .toList();
    }

    private static Source toSource(SeedSourceInfo i) {
        return new Source(
                UUID.nameUUIDFromBytes(("source:" + i.sourceCode()).getBytes()),
                i.sourceCode(),
                i.title(),
                i.publisher(),
                i.year(),
                i.url(),
                i.examFormat(),
                i.attributionText(),
                i.extraJson());
    }

    private static Source stub(SeedSourceInfo i) {
        return toSource(i);
    }
}
