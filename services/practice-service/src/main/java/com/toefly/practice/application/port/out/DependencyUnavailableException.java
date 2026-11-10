package com.toefly.practice.application.port.out;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

/** A synchronous dependency failed; running attempts continue (docs/04-architecture.md §3.6). */
@ProblemType(value = "dependency-unavailable", status = 503)
public class DependencyUnavailableException extends RuntimeException implements ProblemProperties {

    private final String dependency;

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is unavailable right now; try again later.", cause);
        this.dependency = dependency;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("dependency", dependency);
    }
}
