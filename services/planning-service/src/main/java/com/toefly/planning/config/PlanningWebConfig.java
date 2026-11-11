package com.toefly.planning.config;

import com.toefly.planning.adapter.in.web.api.StudyPlansApi;
import com.toefly.planning.adapter.in.web.api.TodayApi;
import com.toefly.planning.adapter.in.web.api.TutorApi;
import com.toefly.planning.adapter.in.web.api.WeekTasksApi;
import com.toefly.planning.adapter.in.web.api.WeeksApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * planning-service.yaml keeps the public paths relative to the server {@code /api/v1} (docs/05 §7.2), while the
 * internal paths carry {@code /internal/v1} themselves; the public controllers get the prefix here.
 */
@Configuration(proxyBeanMethods = false)
class PlanningWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        StudyPlansApi.class, WeeksApi.class, TodayApi.class, WeekTasksApi.class, TutorApi.class));
    }
}
