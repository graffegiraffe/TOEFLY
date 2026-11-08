package com.toefly.content.application.seed;

import java.util.List;

/**
 * The {@code extraction} block of a seed envelope (docs/08-content-pipeline.md §4.7): how the object was read and
 * what the extractor warned about.
 */
public record Extraction(String method, boolean reviewed, List<String> warnings) {

    public Extraction {
        warnings = List.copyOf(warnings);
    }
}
