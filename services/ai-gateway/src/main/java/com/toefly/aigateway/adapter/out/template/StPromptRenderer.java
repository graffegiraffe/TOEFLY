package com.toefly.aigateway.adapter.out.template;

import com.toefly.aigateway.application.port.out.PromptRendererPort;
import java.util.Map;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;

/**
 * Spring AI {@link StTemplateRenderer} with {@code $...$} delimiters, so braces of JSON examples in prompts stay
 * text (§2.9).
 */
public class StPromptRenderer implements PromptRendererPort {

    private final StTemplateRenderer renderer = StTemplateRenderer.builder()
            .startDelimiterToken('$')
            .endDelimiterToken('$')
            .validationMode(ValidationMode.THROW)
            .build();

    @Override
    public String render(String template, Map<String, Object> variables) {
        try {
            return renderer.apply(template, variables);
        } catch (IllegalStateException missing) {
            throw new com.toefly.aigateway.domain.prompt.PromptRuleException("validation-failed", missing.getMessage());
        }
    }
}
