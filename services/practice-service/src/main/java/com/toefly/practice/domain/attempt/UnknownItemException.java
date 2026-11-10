package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import java.util.Map;
import java.util.UUID;

/** The item is not part of the attempt. */
@ProblemType(value = "not-found", status = 404)
public class UnknownItemException extends RuntimeException implements ProblemProperties {

    public UnknownItemException(UUID itemId) {
        super("Item " + itemId + " is not part of this attempt.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("resourceType", "Item");
    }
}
