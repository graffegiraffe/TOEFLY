package com.toefly.aigateway.application.port.out;

import java.util.Map;

/** Renders a template with {@code $variable$} placeholders (Spring AI StTemplateRenderer). */
public interface PromptRendererPort {

    String render(String template, Map<String, Object> variables);
}
