package com.toefly.tutoragent.config;

import com.toefly.tutoragent.domain.learner.Taxonomy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads {@code classpath:learner/mistake-subcategories.json}, the table of docs/06 §6.3. */
final class TaxonomyLoader {

    private TaxonomyLoader() {}

    static Taxonomy load(JsonMapper json) {
        try (InputStream in = new ClassPathResource("learner/mistake-subcategories.json").getInputStream()) {
            JsonNode root = json.readTree(in);
            Map<String, Taxonomy.Entry> entries = new LinkedHashMap<>();
            for (JsonNode e : root.path("entries")) {
                String code = e.path("code").asString();
                entries.put(
                        code,
                        new Taxonomy.Entry(
                                code,
                                capitalize(e.path("description").asString(code)),
                                e.path("descriptionRu").asString(code),
                                e.path("unitCode").isString()
                                        ? e.path("unitCode").asString()
                                        : null,
                                e.path("unitTitle").isString()
                                        ? e.path("unitTitle").asString()
                                        : null));
            }
            return new Taxonomy(entries);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
