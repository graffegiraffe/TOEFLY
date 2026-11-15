package com.toefly.aigateway.domain.routing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The table of §2.4: a route per use case, with separate routes for the passes of the graders. */
public final class RoutingTable {

    private final Map<String, ModelRoute> routes = new HashMap<>();

    public RoutingTable(List<ModelRoute> routes) {
        for (ModelRoute route : routes) {
            if (this.routes.put(key(route.useCase(), route.passTag()), route) != null) {
                throw new IllegalArgumentException("duplicate route " + key(route.useCase(), route.passTag()));
            }
        }
    }

    /** The route of the pass, else the route of the use case. */
    public Optional<ModelRoute> route(AiUseCase useCase, @Nullable String passTag) {
        if (passTag != null) {
            ModelRoute ofPass = routes.get(key(useCase, passTag));
            if (ofPass != null) {
                return Optional.of(ofPass);
            }
        }
        return Optional.ofNullable(routes.get(key(useCase, null)));
    }

    public List<ModelRoute> all() {
        return List.copyOf(routes.values());
    }

    private static String key(AiUseCase useCase, @Nullable String passTag) {
        return passTag == null ? useCase.name() : useCase + "#" + passTag;
    }
}
