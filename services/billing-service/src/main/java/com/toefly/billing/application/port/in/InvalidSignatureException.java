package com.toefly.billing.application.port.in;

/** T-F6-01: the body is rejected before processing; 400 webhook-signature-invalid. */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
