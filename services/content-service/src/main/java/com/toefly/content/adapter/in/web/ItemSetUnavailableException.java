package com.toefly.content.adapter.in.web;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "item-set-unavailable", status = 409)
class ItemSetUnavailableException extends RuntimeException {

    ItemSetUnavailableException() {
        super("The item set is hidden or not published.");
    }
}
