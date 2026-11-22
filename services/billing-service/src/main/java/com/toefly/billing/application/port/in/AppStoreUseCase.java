package com.toefly.billing.application.port.in;

public interface AppStoreUseCase {

    /** AC-27.04.2: a signed StoreKit 2 transaction runs the same saga as a web payment. */
    SubscriptionView submit(Caller caller, String signedTransactionInfo);
}
