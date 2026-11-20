package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;

/** One read model: applies events and can be emptied for a rebuild. */
interface Projection {

    void apply(AnalyticsEvent event);

    /** Empties the tables of the read model. */
    void reset();
}
