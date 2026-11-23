package com.toefly.vocabulary.config;

import com.toefly.vocabulary.adapter.in.web.api.DictionaryApi;
import com.toefly.vocabulary.adapter.in.web.api.ReviewsApi;
import com.toefly.vocabulary.adapter.in.web.api.SettingsApi;
import com.toefly.vocabulary.adapter.in.web.api.SuggestionsApi;
import com.toefly.vocabulary.adapter.in.web.api.WordSetsApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** vocabulary-service.yaml keeps the public paths relative to {@code /api/v1}; internal paths carry {@code /internal/v1}. */
@Configuration(proxyBeanMethods = false)
class VocabularyWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        DictionaryApi.class,
                        ReviewsApi.class,
                        WordSetsApi.class,
                        SuggestionsApi.class,
                        SettingsApi.class));
    }
}
