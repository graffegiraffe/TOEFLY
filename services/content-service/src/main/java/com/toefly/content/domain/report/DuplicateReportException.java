package com.toefly.content.domain.report;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "conflict", status = 409)
public class DuplicateReportException extends RuntimeException {

    public DuplicateReportException() {
        super("you already have an open report for this item");
    }
}
