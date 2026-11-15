package com.toefly.aigateway.adapter.out.template;

import com.toefly.aigateway.application.port.out.PromptSourcePort;
import com.toefly.aigateway.domain.prompt.PromptParams;
import com.toefly.aigateway.domain.prompt.PromptStatus;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prompt files {@code classpath:prompts/<key>/v<N>.yaml} (§2.9). An output schema is inline ({@code outputSchema})
 * or a file {@code classpath:schemas/<outputSchemaRef>.json}.
 */
public class PromptFileSource implements PromptSourcePort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String location;
    private final Clock clock;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public PromptFileSource(String location, Clock clock) {
        this.location = location;
        this.clock = clock;
    }

    @Override
    public List<Source> load() {
        List<Source> sources = new ArrayList<>();
        try {
            for (Resource file : resolver.getResources(location + "/*/v*.yaml")) {
                sources.add(source(file, YamlFiles.read(file)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot list prompt files in " + location, e);
        }
        return sources;
    }

    private Source source(Resource file, Map<String, Object> yaml) throws IOException {
        String key = string(yaml, "key");
        AiUseCase useCase = AiUseCase.valueOf(string(yaml, "useCase"));
        String description = String.valueOf(yaml.getOrDefault("description", ""));
        String owner = String.valueOf(yaml.getOrDefault("owner", "ai-gateway"));
        String schema = schema(yaml);
        Map<?, ?> params = yaml.get("params") instanceof Map<?, ?> map ? map : Map.of();
        @SuppressWarnings("unchecked")
        List<String> untrusted = yaml.get("untrusted") instanceof List<?> list ? (List<String>) list : List.of();
        String system = string(yaml, "system");
        String user = string(yaml, "user");
        PromptVersion version = new PromptVersion(
                key,
                ((Number) yaml.get("version")).intValue(),
                useCase,
                description,
                system,
                user,
                schema,
                new PromptParams(
                        params.get("temperature") instanceof Number n ? n.doubleValue() : null,
                        params.get("maxOutputTokens") instanceof Number n ? n.intValue() : null,
                        params.get("reasoningEffort") == null
                                ? null
                                : params.get("reasoningEffort").toString()),
                yaml.get("maxInputTokens") instanceof Number n ? n.intValue() : PromptVersion.DEFAULT_MAX_INPUT_TOKENS,
                Set.copyOf(untrusted),
                GuardPolicy.valueOf(String.valueOf(yaml.getOrDefault("guardPolicy", "NONE"))),
                yaml.get("evalSuite") == null ? null : yaml.get("evalSuite").toString(),
                PromptStatus.DRAFT,
                sha256(system + "\u0000" + user + "\u0000" + schema),
                "repository:" + file.getFilename(),
                clock.instant());
        return new Source(new PromptTemplate(key, useCase, description, owner, null, null, 0), version);
    }

    private @Nullable String schema(Map<String, Object> yaml) throws IOException {
        Object inline = yaml.get("outputSchema");
        if (inline != null) {
            return inline instanceof String text ? text : JSON.writeValueAsString(inline);
        }
        Object ref = yaml.get("outputSchemaRef");
        if (ref == null) {
            return null;
        }
        Resource file = resolver.getResource("classpath:schemas/" + ref + ".json");
        try (InputStream in = file.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String string(Map<String, Object> yaml, String field) {
        Object value = yaml.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("prompt file without " + field + ": " + yaml.get("key"));
        }
        return value.toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
