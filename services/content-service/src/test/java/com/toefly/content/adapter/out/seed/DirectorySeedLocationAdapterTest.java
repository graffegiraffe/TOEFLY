package com.toefly.content.adapter.out.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.content.application.port.out.SeedFileException;
import com.toefly.content.application.seed.SeedManifest.FileRef;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectorySeedLocationAdapterTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("an empty directory has no manifest, so there is nothing to import")
    void noManifest() {
        assertThat(new DirectorySeedLocationAdapter(root).manifest()).isEmpty();
    }

    @Test
    @DisplayName("a file listed in the manifest but missing on disk is a seed error")
    void missingFile() {
        var adapter = new DirectorySeedLocationAdapter(root);

        assertThatThrownBy(() -> adapter.itemSet(new FileRef("a/b.json", "a/b", "ITEM_SET", "sha256:0")))
                .isInstanceOf(SeedFileException.class)
                .hasCauseInstanceOf(java.io.IOException.class);
    }
}
