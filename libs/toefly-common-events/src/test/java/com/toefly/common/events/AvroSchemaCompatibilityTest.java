package com.toefly.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.junit.jupiter.api.Test;

/**
 * Every schema must read data written with the same schema on the base branch (the baseline directory is checked out
 * by CI and passed as {@code -Dtoefly.avro.baseline}). Together with the registry rule this keeps
 * {@code BACKWARD_TRANSITIVE}: the baseline itself was compatible with all earlier versions.
 */
class AvroSchemaCompatibilityTest {

    @Test
    void currentSchemasReadBaselineData() throws IOException {
        String baseline = System.getProperty("toefly.avro.baseline", "");
        assumeTrue(!baseline.isBlank() && Files.isDirectory(Path.of(baseline)), "no baseline schemas given");

        List<String> problems =
                incompatibilities(Path.of(System.getProperty("toefly.avro.schemas")), Path.of(baseline));

        assertThat(problems).isEmpty();
    }

    @Test
    void detectsIncompatibleChange() throws IOException {
        Path fixtures = Path.of("src/test/resources/avro");

        List<String> problems = incompatibilities(fixtures.resolve("incompatible"), fixtures.resolve("baseline"));

        assertThat(problems).singleElement().asString().contains("demo/DemoItemCreatedV1.avsc");
    }

    @Test
    void acceptsFieldWithDefault() throws IOException {
        Path fixtures = Path.of("src/test/resources/avro");

        assertThat(incompatibilities(fixtures.resolve("compatible"), fixtures.resolve("baseline")))
                .isEmpty();
    }

    static List<String> incompatibilities(Path current, Path baseline) throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(baseline)) {
            for (Path old : files.filter(p -> p.toString().endsWith(".avsc")).toList()) {
                Path relative = baseline.relativize(old);
                Path updated = current.resolve(relative);
                if (!Files.exists(updated)) {
                    problems.add(relative + ": schema removed");
                    continue;
                }
                Schema writer = new Schema.Parser().parse(old.toFile());
                Schema reader = new Schema.Parser().parse(updated.toFile());
                var result = SchemaCompatibility.checkReaderWriterCompatibility(reader, writer);
                if (result.getType() != SchemaCompatibilityType.COMPATIBLE) {
                    problems.add(relative + ": " + result.getResult().getIncompatibilities());
                }
            }
        }
        return problems;
    }
}
