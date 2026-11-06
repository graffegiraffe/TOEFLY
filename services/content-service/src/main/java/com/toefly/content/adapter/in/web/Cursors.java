package com.toefly.content.adapter.in.web;

import com.toefly.content.application.port.out.CatalogQueryPort.Filter;

final class Cursors {

    private Cursors() {}

    /** A cursor is bound to the filters it was issued for (docs/05-api-contracts.md §2.4). */
    static String fingerprint(Filter f) {
        return String.join("|", String.valueOf(f.section()), String.valueOf(f.taskType()),
                String.valueOf(f.examFormat()), String.valueOf(f.provenance()), String.valueOf(f.cefr()),
                String.valueOf(f.legacy()), String.valueOf(f.sourceCode()), String.valueOf(f.status()),
                String.valueOf(f.reported()));
    }
}
