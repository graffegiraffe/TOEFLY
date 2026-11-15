package com.toefly.aigateway.adapter.out.template;

import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import com.toefly.aigateway.domain.routing.RouteParams;
import com.toefly.aigateway.domain.routing.RoutingTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * The table of §2.4 from {@code ai-gateway-routes.yaml}: the classpath copy, or the ConfigMap file when one is
 * mounted. {@link #refresh()} re-reads the file when its modification time changes (decision 9 of phase 5a); a
 * broken file keeps the previous table.
 */
public class RoutesFileAdapter implements RoutingTablePort {

    private static final Logger LOG = LoggerFactory.getLogger(RoutesFileAdapter.class);

    private final @Nullable Path override;
    private final AtomicReference<RoutingTable> table = new AtomicReference<>();
    private volatile @Nullable FileTime loadedAt;

    public RoutesFileAdapter(@Nullable String overridePath) {
        this.override = overridePath == null || overridePath.isBlank() ? null : Path.of(overridePath);
        table.set(parse(resource()));
        loadedAt = modified();
    }

    @Override
    public RoutingTable current() {
        return table.get();
    }

    public void refresh() {
        FileTime now = modified();
        if (now == null || now.equals(loadedAt)) {
            return;
        }
        try {
            table.set(parse(resource()));
            loadedAt = now;
            LOG.info("Reloaded AI routes from {}", override);
        } catch (RuntimeException broken) {
            LOG.error("Kept the previous AI routes: {} is invalid", override, broken);
        }
    }

    private Resource resource() {
        if (override != null && Files.isReadable(override)) {
            return new FileSystemResource(override);
        }
        return new ClassPathResource("ai-gateway-routes.yaml");
    }

    private @Nullable FileTime modified() {
        if (override == null || !Files.isReadable(override)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(override);
        } catch (IOException e) {
            return null;
        }
    }

    static RoutingTable parse(Resource resource) {
        Map<String, Object> yaml = YamlFiles.read(resource);
        List<ModelRoute> routes = new ArrayList<>();
        for (Object item : (List<?>) yaml.get("routes")) {
            Map<?, ?> route = (Map<?, ?>) item;
            List<ModelRef> candidates = ((List<?>) route.get("candidates"))
                    .stream().map(Object::toString).map(ModelRef::parse).toList();
            Object passTag = route.get("passTag");
            Object effort = route.get("reasoningEffort");
            routes.add(new ModelRoute(
                    AiUseCase.valueOf(route.get("useCase").toString()),
                    passTag == null ? null : passTag.toString(),
                    candidates,
                    new RouteParams(
                            number(route.get("temperature"), 0.0),
                            (int) number(route.get("maxOutputTokens"), 0),
                            effort == null ? null : effort.toString(),
                            Boolean.TRUE.equals(route.get("cacheable")),
                            route.get("timeout") == null
                                    ? Duration.ofSeconds(60)
                                    : Duration.parse(route.get("timeout").toString()))));
        }
        return new RoutingTable(routes);
    }

    private static double number(@Nullable Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
