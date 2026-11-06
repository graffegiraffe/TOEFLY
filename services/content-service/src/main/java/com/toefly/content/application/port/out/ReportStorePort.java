package com.toefly.content.application.port.out;

import com.toefly.content.domain.report.ItemReport;
import com.toefly.content.domain.report.ReportStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ReportStorePort {

    /** @return false if the same reporter already has an open report for the same target */
    boolean insert(ItemReport report);

    Optional<ItemReport> findById(UUID id);

    void update(ItemReport report);

    List<ItemReport> list(@Nullable ReportStatus status, int limit);
}
