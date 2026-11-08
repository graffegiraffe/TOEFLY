package com.toefly.content.domain.catalog;

import com.toefly.common.web.ProblemType;
import java.util.UUID;

@ProblemType(value = "not-found", status = 404)
public class UnitNotFoundException extends RuntimeException {

    public UnitNotFoundException(UUID id) {
        super("unit " + id + " not found");
    }
}
