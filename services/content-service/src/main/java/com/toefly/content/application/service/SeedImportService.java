package com.toefly.content.application.service;

import com.toefly.content.application.port.in.ImportReport;
import com.toefly.content.application.port.in.ImportSeedUseCase;
import com.toefly.content.application.port.out.SeedImportStorePort;
import com.toefly.content.application.port.out.SeedLocationPort;
import com.toefly.content.application.seed.SeedManifest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Imports every source of the manifest in its own transaction; a failing source does not block the others. */
@Service
public class SeedImportService implements ImportSeedUseCase {

    private static final Logger log = LoggerFactory.getLogger(SeedImportService.class);

    private final SeedLocationPort seed;
    private final SeedImportStorePort imports;
    private final SourceImporter importer;
    private final Clock clock;

    public SeedImportService(SeedLocationPort seed, SeedImportStorePort imports, SourceImporter importer, Clock clock) {
        this.seed = seed;
        this.imports = imports;
        this.importer = importer;
        this.clock = clock;
    }

    @Override
    public ImportReport run(Optional<String> sourceCode, boolean dryRun) {
        Optional<SeedManifest> manifest = seed.manifest();
        if (manifest.isEmpty()) {
            return new ImportReport(dryRun, List.of());
        }
        List<ImportReport.SourceResult> results = new ArrayList<>();
        for (SeedManifest.SourceBundle bundle : manifest.get().sources()) {
            if (sourceCode.isPresent() && !sourceCode.get().equals(bundle.sourceCode())) {
                continue;
            }
            results.add(importOne(bundle, dryRun));
        }
        return new ImportReport(dryRun, results);
    }

    private ImportReport.SourceResult importOne(SeedManifest.SourceBundle bundle, boolean dryRun) {
        Instant started = clock.instant();
        try {
            ImportReport.SourceResult result = importer.importSource(bundle, dryRun);
            log.info("Seed source {} imported: {}", bundle.sourceCode(), result);
            return result;
        } catch (RuntimeException e) {
            log.error("Seed source {} failed", bundle.sourceCode(), e);
            if (!dryRun) {
                imports.record(
                        bundle.sourceCode(),
                        bundle.bundleSha256(),
                        "FAILED",
                        Map.of(),
                        "{\"error\":" + quote(String.valueOf(e.getMessage())) + "}",
                        started,
                        clock.instant());
            }
            return ImportReport.SourceResult.failed(bundle.sourceCode(), String.valueOf(e.getMessage()));
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ") + "\"";
    }
}
