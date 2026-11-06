package com.toefly.content.domain.catalog;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "conflict", status = 409)
public class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException(ItemStatus from, ItemStatus to) {
        super("ItemSet cannot move from " + from + " to " + to);
    }
}
