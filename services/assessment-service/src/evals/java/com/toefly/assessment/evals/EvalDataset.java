package com.toefly.assessment.evals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Suites {@code evals/data/<suite>.jsonl}, checked against the hashes of {@code evals/manifest.json}. */
public final class EvalDataset {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private EvalDataset() {}

    public static List<EvalCase> load(Path root, String suite) throws IOException {
        Path file = root.resolve("evals/data/" + suite + ".jsonl");
        JsonNode manifest = JSON.readTree(Files.readString(root.resolve("evals/manifest.json")));
        String expected = manifest.path("suites").path(suite).path("sha256").asString("");
        String actual = sha256(Files.readAllBytes(file));
        if (!expected.equals(actual)) {
            throw new IllegalStateException(suite + ": data does not match evals/manifest.json (" + actual + ")");
        }
        List<EvalCase> cases = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                cases.add(JSON.readValue(line, EvalCase.class));
            }
        }
        return cases;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
