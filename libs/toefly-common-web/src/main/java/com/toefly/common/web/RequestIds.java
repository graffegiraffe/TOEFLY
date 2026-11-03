package com.toefly.common.web;

/** Request correlation names (docs/05-api-contracts.md §2.11). */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private RequestIds() {}
}
