package com.toefly.common.events;

/** A record arrived without a mandatory {@code ce_*} header; it is sent to the DLQ without retries. */
public class MissingCloudEventHeaderException extends NonRetryableEventException {

    public MissingCloudEventHeaderException(String header) {
        super("Missing CloudEvents header " + header);
    }
}
