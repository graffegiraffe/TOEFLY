package com.toefly.planning.adapter.in.web;

import com.toefly.common.web.ProblemType;

/** Operations of the tutor and of later phases (docs/12, phase 4, decision 5). */
@ProblemType(value = "not-implemented", status = 501)
class NotImplementedException extends RuntimeException {

    NotImplementedException(String operation) {
        super(operation + " opens in a later phase.");
    }
}
