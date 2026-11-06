package com.toefly.content.domain.catalog;

import com.toefly.common.web.ProblemType;
import java.util.UUID;

@ProblemType(value = "not-found", status = 404)
public class ItemSetNotFoundException extends RuntimeException {

    public ItemSetNotFoundException(UUID id) {
        super("item set " + id + " not found");
    }
}
