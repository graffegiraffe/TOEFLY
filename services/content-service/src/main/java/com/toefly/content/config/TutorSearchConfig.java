package com.toefly.content.config;

import com.toefly.common.security.InternalUserAccess;
import com.toefly.content.adapter.in.web.api.InternalApi;
import com.toefly.content.domain.catalog.MistakeTaxonomy;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The searches for the tutor agent: the mistake taxonomy and who may call them (docs/12 phase 5c decision 2). */
@Configuration(proxyBeanMethods = false)
class TutorSearchConfig {

    /** A copy of the table of docs/06 §6.3 that assessment-service keeps in {@code grading/}. */
    static final String TAXONOMY = "taxonomy/mistake-subcategories.json";

    @Bean
    MistakeTaxonomy mistakeTaxonomy() throws IOException {
        try (InputStream in = new ClassPathResource(TAXONOMY).getInputStream()) {
            return taxonomy(JsonMapper.builder().build().readTree(in));
        }
    }

    static MistakeTaxonomy taxonomy(JsonNode root) {
        Map<String, String> codes = new HashMap<>();
        for (JsonNode entry : root.path("entries")) {
            JsonNode unit = entry.path("unitCode");
            if (unit.isString()) {
                codes.put(entry.path("code").asString(), unit.asString());
            }
        }
        return new MistakeTaxonomy(codes);
    }

    /** CC|JWT (docs/05 §9.1): the catalog is not personal, so any user token may search it besides the service. */
    @Bean
    InternalUserAccess internalUserAccess() {
        return new InternalUserAccess(
                List.of(),
                List.of(InternalApi.PATH_SEARCH_ITEM_SETS_INTERNAL, InternalApi.PATH_SEARCH_GRAMMAR_UNITS_INTERNAL));
    }
}
