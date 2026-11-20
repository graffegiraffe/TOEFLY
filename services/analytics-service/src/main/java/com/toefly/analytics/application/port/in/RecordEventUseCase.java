package com.toefly.analytics.application.port.in;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;

/** Writes a consumed event to the journal and applies it to every read model in the same transaction. */
public interface RecordEventUseCase {

    /** @return {@code false} when the journal already holds the event (INV-ANL-01) */
    boolean record(EventMeta meta, AnalyticsEvent event);
}
