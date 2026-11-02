package com.toefly.common.web;

import org.jspecify.annotations.Nullable;

/** Cursor page metadata: {@code { "nextCursor": "...", "hasMore": true, "limit": 20 }}. */
public record PageInfo(@Nullable String nextCursor, boolean hasMore, int limit) {}
