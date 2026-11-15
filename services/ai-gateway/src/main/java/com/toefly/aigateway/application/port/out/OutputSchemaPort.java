package com.toefly.aigateway.application.port.out;

import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** JSON of a structured answer: parsing, JSON Schema checks and rewriting of its string values. */
public interface OutputSchemaPort {

    /** The JSON object in a model answer (code fences and text around it removed), or null if there is none. */
    @Nullable
    Object parse(String answer);

    /** Violations of the schema; empty when the value is valid. */
    List<String> validate(String schema, Object value);

    /** The same JSON with every string value passed through {@code strings}. */
    Object mapStrings(Object value, UnaryOperator<String> strings);

    String write(Object value);
}
