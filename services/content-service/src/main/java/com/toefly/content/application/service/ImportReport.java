package com.toefly.content.application.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Outcome of an import run, per source. */
public record ImportReport(boolean dryRun, List<SourceResult> sources) {

    public record SourceResult(
            String sourceCode, String status, int created, int updated, int unchanged, int hidden,
            @Nullable String error) {

        static SourceResult skipped(String code) {
            return new SourceResult(code, "SKIPPED", 0, 0, 0, 0, null);
        }

        static SourceResult failed(String code, String error) {
            return new SourceResult(code, "FAILED", 0, 0, 0, 0, error);
        }
    }
}
