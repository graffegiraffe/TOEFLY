package com.toefly.profile.adapter.in.web;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;

@ProblemType(value = "not-found", status = 404)
class ResourceNotFoundException extends RuntimeException implements ProblemProperties {

    private final String resourceType;

    ResourceNotFoundException(String resourceType) {
        super("The resource does not exist.");
        this.resourceType = resourceType;
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("resourceType", resourceType);
    }
}
