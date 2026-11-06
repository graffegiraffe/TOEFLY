package com.toefly.content.application.seed;

import java.util.List;

/** {@code manifest.json} of a seed bundle (docs/08-content-pipeline.md §4.9). */
public record SeedManifest(List<SourceBundle> sources) {

    public record SourceBundle(String sourceCode, String bundleSha256, List<FileRef> files) {}

    public record FileRef(String path, String seedId, String kind, String sha256) {}
}
