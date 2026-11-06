package com.toefly.content.config;

import com.toefly.content.application.port.in.ImportSeedUseCase;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Imports the seed bundle at start-up (docs/08-content-pipeline.md §4.10); safe on every restart. */
@Component
@ConditionalOnProperty(name = "toefly.content.seed.enabled", havingValue = "true")
class SeedImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedImportRunner.class);

    private final ImportSeedUseCase importer;

    SeedImportRunner(ImportSeedUseCase importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seed import: {}", importer.run(Optional.empty(), false));
    }
}
