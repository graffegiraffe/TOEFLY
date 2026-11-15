package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.domain.chat.ToolSpec;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** A tool the model may call but the gateway never runs: the agent loop and its tools live in the caller. */
record DefinitionOnlyTool(ToolDefinition definition) implements ToolCallback {

    static DefinitionOnlyTool of(ToolSpec spec) {
        return new DefinitionOnlyTool(ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(spec.parametersSchema())
                .build());
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        throw new UnsupportedOperationException("ai-gateway does not execute tools: " + definition.name());
    }
}
