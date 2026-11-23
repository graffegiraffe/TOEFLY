package com.toefly.tutoragent.config;

import com.toefly.tutoragent.application.port.out.ChatModelPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Signatures of the agent's tools, {@code classpath:chat/tools.json} (docs/06 §4.5.1). */
final class ToolSpecsLoader {

    private ToolSpecsLoader() {}

    static List<ChatModelPort.ToolSpec> load(JsonMapper json) {
        try (InputStream in = new ClassPathResource("chat/tools.json").getInputStream()) {
            List<ChatModelPort.ToolSpec> specs = new ArrayList<>();
            for (JsonNode tool : json.readTree(in)) {
                specs.add(new ChatModelPort.ToolSpec(
                        tool.path("name").asString(),
                        tool.path("description").asString(),
                        json.writeValueAsString(tool.path("parameters"))));
            }
            return specs;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
