package com.toefly.practice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0010: events are never updated; the only DELETE is the GDPR erasure of a user's streams. */
class EventStoreImmutabilityTest {

    private static final Pattern UPDATE = Pattern.compile("UPDATE\\s+attempt_event", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE = Pattern.compile("DELETE\\s+FROM\\s+attempt_event", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("ADR-0010: no UPDATE of attempt_event and DELETE only in eraseUser")
    void appendOnly() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            String code = Files.readString(source);
            assertThat(UPDATE.matcher(code).find()).as(source.toString()).isFalse();
            long deletes = DELETE.matcher(code).results().count();
            if (deletes > 0) {
                assertThat(source.getFileName().toString()).isEqualTo("JdbcAttemptStoreAdapter.java");
                assertThat(deletes).isEqualTo(1);
                assertThat(code).contains("public int eraseUser(UUID userId)");
            }
        }
    }
}
