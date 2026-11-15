package com.toefly.aigateway.domain.chat;

/** A function the caller offers the model; {@code parametersSchema} is a JSON Schema as text. */
public record ToolSpec(String name, String description, String parametersSchema) {}
