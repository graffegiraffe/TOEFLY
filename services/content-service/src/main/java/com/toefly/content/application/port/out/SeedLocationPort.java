package com.toefly.content.application.port.out;

import com.toefly.content.application.seed.SeedItemSet;
import com.toefly.content.application.seed.SeedManifest;
import com.toefly.content.application.seed.SeedSourceInfo;
import com.toefly.content.application.seed.SeedUnit;
import java.util.Optional;

/** Where the seed bundle lives: a directory on disk or the private {@code toefly-seed} bucket. */
public interface SeedLocationPort {

    Optional<SeedManifest> manifest();

    /** Reads {@code <sourceCode>/source.json}. */
    SeedSourceInfo source(String sourceCode);

    /**
     * Reads and verifies one item-set file.
     *
     * @throws SeedFileException if the hash differs from the manifest or the file is malformed
     */
    SeedItemSet itemSet(SeedManifest.FileRef file);

    /** Reads and verifies one grammar or vocabulary unit file. */
    SeedUnit unit(SeedManifest.FileRef file);
}
