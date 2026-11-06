package com.toefly.content.adapter.out.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.content.application.port.out.SeedFileException;
import com.toefly.content.application.seed.SeedManifest.FileRef;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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

    @Test
    @DisplayName("UUID v5 equals the value the Python scripts compute for the same seed id")
    void uuidV5MatchesPython() {
        assertThat(Uuids.itemSet("a/b")).hasToString("5e72271e-bd13-5aa1-ac46-70c238a7e33f");
        assertThat(Uuids.item("a/b", 3)).hasToString("8649de9d-a7f9-56b0-9d5b-21f94a873293");
    }

    static boolean realSeedPresent() {
        return java.nio.file.Files.exists(REAL.resolve("manifest.json"));
    }

    static final Path REAL = Path.of("../../content/seed");

    @Test
    @EnabledIf("realSeedPresent")
    @DisplayName("the locally extracted bundle is readable and its ids equal those of the scripts")
    void readsTheRealBundle() throws Exception {
        var adapter = new DirectorySeedLocationAdapter(REAL);
        var manifest = adapter.manifest().orElseThrow();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        int items = 0;
        for (var bundle : manifest.sources()) {
            assertThat(adapter.source(bundle.sourceCode()).attributionText()).isNotBlank();
            for (var file : bundle.files()) {
                var set = adapter.itemSet(file);
                var envelope = json.readTree(java.nio.file.Files.readString(REAL.resolve(file.path())));
                assertThat(set.id()).hasToString(envelope.get("id").asString());
                assertThat(set.contentHash())
                        .isEqualTo(envelope.get("contentHash").asString().substring("sha256:".length()));
                items += set.items().size();
                assertThat(set.items())
                        .hasSize(envelope.get("itemSet")
                                .get("payload")
                                .get("items")
                                .size());
            }
        }
        assertThat(items).isGreaterThan(0);
    }
}
