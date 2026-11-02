package com.toefly.common.web;

import java.util.List;

/** Response body of a cursor-paginated list (docs/05-api-contracts.md §2.4). */
public record CursorPage<T>(List<T> items, PageInfo page) {}
