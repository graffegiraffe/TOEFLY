package com.toefly.aigateway.domain.chat;

/** A function the model asks the caller to run; {@code arguments} is a JSON object as text. */
public record ToolCall(String id, String name, String arguments) {}
