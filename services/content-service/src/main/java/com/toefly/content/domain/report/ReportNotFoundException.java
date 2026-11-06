package com.toefly.content.domain.report;

import com.toefly.common.web.ProblemType;
import java.util.UUID;

@ProblemType(value = "not-found", status = 404)
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(UUID id) {
        super("report " + id + " not found");
    }
}
