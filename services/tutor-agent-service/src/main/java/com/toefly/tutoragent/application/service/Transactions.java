package com.toefly.tutoragent.application.service;

import java.util.function.Supplier;

/** Transaction boundary of the application layer, implemented by the configuration. */
public interface Transactions {

    void run(Runnable work);

    <T> T call(Supplier<T> work);
}
