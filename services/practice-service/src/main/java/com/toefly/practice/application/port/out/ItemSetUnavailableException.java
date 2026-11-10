package com.toefly.practice.application.port.out;

import com.toefly.common.web.ProblemType;
import java.util.UUID;

/** The set is hidden, not published or unknown (INV-ATT-06). */
@ProblemType(value = "item-set-unavailable", status = 409)
public class ItemSetUnavailableException extends RuntimeException {

    public ItemSetUnavailableException(UUID itemSetId) {
        super("Item set " + itemSetId + " is not available for practice.");
    }
}
