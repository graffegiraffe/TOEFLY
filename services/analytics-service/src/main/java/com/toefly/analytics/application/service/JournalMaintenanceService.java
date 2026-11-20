package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.MaintainJournalUseCase;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a partition for this and the next two months and drops events older than 400 days. */
@Service
public class JournalMaintenanceService implements MaintainJournalUseCase {

    private final EventJournalRepository journal;
    private final Clock clock;

    public JournalMaintenanceService(EventJournalRepository journal, Clock clock) {
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void maintain() {
        YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        journal.ensurePartitions(month, month.plusMonths(2));
        journal.deleteBefore(clock.instant().minus(Duration.ofDays(RETENTION_DAYS)));
    }
}
