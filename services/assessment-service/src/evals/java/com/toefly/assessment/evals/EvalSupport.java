package com.toefly.assessment.evals;

import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.service.GradingKit;
import com.toefly.assessment.application.service.WritingGrader;
import com.toefly.assessment.config.GradingKitLoader;
import java.nio.file.Path;
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

    static WritingGrader grader(AiGradingPort ai) {
        return new WritingGrader(ai, KIT);
    }
}
