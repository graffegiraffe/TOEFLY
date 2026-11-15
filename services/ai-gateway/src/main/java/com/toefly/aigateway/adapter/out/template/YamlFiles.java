package com.toefly.aigateway.adapter.out.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Safe YAML reading of prompt and route files (no custom tags). */
final class YamlFiles {

    private YamlFiles() {}

    static Map<String, Object> read(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalStateException(resource + " is not a YAML mapping");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }
}
