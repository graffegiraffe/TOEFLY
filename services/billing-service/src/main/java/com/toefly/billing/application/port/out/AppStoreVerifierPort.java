package com.toefly.billing.application.port.out;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Checks a StoreKit 2 JWS against the certificate chain (Apple or, locally, StoreKit Testing). */
public interface AppStoreVerifierPort {

    Transaction verify(String signedTransactionInfo);

    /** The fields of a verified transaction that billing needs. */
    record Transaction(
            String transactionId,
            String originalTransactionId,
            String productId,
            @Nullable UUID appAccountToken,
            Instant purchaseDate,
            Instant expiresDate,
            boolean sandbox,
            @Nullable Integer priceMinor,
            @Nullable String currency) {}
}
