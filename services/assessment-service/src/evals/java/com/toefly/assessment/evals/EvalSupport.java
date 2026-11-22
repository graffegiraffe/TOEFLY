package com.toefly.assessment.evals;

import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.service.GradingKit;
import com.toefly.assessment.application.service.WritingGrader;
import com.toefly.assessment.config.GradingKitLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/** Paths and the wired grader for the evals. */
final class EvalSupport {

    static final GradingKit KIT = GradingKitLoader.load(JsonMapper.builder().build());

    private EvalSupport() {}

    static Path root() {
        return Path.of(System.getProperty("evals.root", "../..")).toAbsolutePath().normalize();
    }

    static Path module() {
        return root().resolve("services/assessment-service");
    }

    static Path reports() {
        return module().resolve("src/evals/reports");
    }

    static Path prompt(String key, int version) {
        return root().resolve("services/ai-gateway/src/main/resources/prompts/" + key + "/v" + version + ".yaml");
    }

    /** The newest prompt file of a key: evals check the candidate version before it is activated (decision 16). */
    static int latestPromptVersion(String key) {
        try (Stream<Path> files = Files.list(prompt(key, 1).getParent())) {
            return files.map(f -> f.getFileName().toString())
                    .filter(n -> n.matches("v\\d+\\.yaml"))
                    .mapToInt(n -> Integer.parseInt(n.substring(1, n.length() - 5)))
                    .max()
                    .orElseThrow();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static WritingGrader grader(AiGradingPort ai) {
        return new WritingGrader(ai, KIT);
    }
}
