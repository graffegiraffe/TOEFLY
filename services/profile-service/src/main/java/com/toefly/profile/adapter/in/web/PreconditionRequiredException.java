package com.toefly.profile.adapter.in.web;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "precondition-required", status = 428)
class PreconditionRequiredException extends RuntimeException {

    PreconditionRequiredException() {
        super("Send If-Match with the ETag of the version you are changing.");
    }
}
