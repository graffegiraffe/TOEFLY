package com.toefly.assessment.config;

import com.toefly.assessment.application.service.GradingKit;
import com.toefly.assessment.domain.grading.Taxonomy;
import com.toefly.assessment.domain.submission.TaskType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Taxonomy of docs/06 §6.3 and the synthetic anchors from {@code classpath:grading/}; also used by the evals. */
public final class GradingKitLoader {

    private GradingKitLoader() {}

    public static GradingKit load(JsonMapper json) {
        Map<String, Taxonomy.Entry> entries = new HashMap<>();
        for (JsonNode e : read(json, "grading/mistake-subcategories.json").path("entries")) {
            entries.put(
                    e.path("code").asString(),
                    new Taxonomy.Entry(
                            e.path("code").asString(),
                            e.path("description").asString(""),
                            e.path("unitCode").isNull()
                                    ? null
                                    : e.path("unitCode").asString(),
                            e.path("unitTitle").isNull()
                                    ? null
                                    : e.path("unitTitle").asString()));
        }
        Map<TaskType, List<GradingKit.Anchor>> anchors = new EnumMap<>(TaskType.class);
        for (JsonNode a : read(json, "grading/anchors.json").path("anchors")) {
            anchors.computeIfAbsent(TaskType.valueOf(a.path("taskType").asString()), k -> new ArrayList<>())
                    .add(new GradingKit.Anchor(
                            a.path("pass").asString(),
                            a.path("score").asInt(),
                            a.path("task").asString(),
                            a.path("text").asString(),
                            a.path("reason").asString()));
        }
        return new GradingKit(new Taxonomy(entries), anchors);
    }

    private static JsonNode read(JsonMapper json, String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readTree(in);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
