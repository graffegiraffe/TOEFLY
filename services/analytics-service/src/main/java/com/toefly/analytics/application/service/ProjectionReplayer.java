package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.ProjectionName;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replays the journal into emptied tables in one transaction: readers see the old view until the commit, and the lock
 * on the journal makes listeners wait, so no event is applied twice or lost.
 */
@Component
public class ProjectionReplayer {

    private final EventJournalRepository journal;
    private final Projections projections;

    public ProjectionReplayer(EventJournalRepository journal, Projections projections) {
        this.journal = journal;
        this.projections = projections;
    }

    @Transactional
    public long replay(ProjectionName name) {
        journal.lockForReplay();
        List<Projection> chain = projections.chain(name);
        chain.forEach(Projection::reset);
        return journal.replay(event -> chain.forEach(projection -> projection.apply(event)));
    }
}
