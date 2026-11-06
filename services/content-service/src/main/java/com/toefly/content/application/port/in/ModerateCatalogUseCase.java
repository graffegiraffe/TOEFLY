package com.toefly.content.application.port.in;

import com.toefly.content.domain.catalog.HideReason;
import com.toefly.content.domain.catalog.ItemSet;
import com.toefly.content.domain.catalog.ItemStatus;
import com.toefly.content.domain.report.ItemReport;
import com.toefly.content.domain.report.ReportReason;
import com.toefly.content.domain.report.ReportStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Status changes, source takedown and the complaint queue. */
public interface ModerateCatalogUseCase {

    ItemSet changeStatus(UUID id, ItemStatus target, HideReason reasonIfHidden);

    int hideSource(String sourceCode);

    ItemReport report(
            UUID reporter, UUID itemSetId, @Nullable Integer ordinal, ReportReason reason, @Nullable String comment);

    ItemReport resolve(UUID reportId, UUID admin, ReportStatus outcome, boolean hideItemSet);

    List<ItemReport> reports(@Nullable ReportStatus status, int limit);
}
