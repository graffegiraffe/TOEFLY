package com.toefly.content.domain.catalog;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "not-found", status = 404)
public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(String code) {
        super("source " + code + " not found");
    }
}
