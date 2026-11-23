package com.toefly.analytics.config;

import com.toefly.analytics.adapter.in.web.api.ActivityApi;
import com.toefly.analytics.adapter.in.web.api.AdminMetricsApi;
import com.toefly.analytics.adapter.in.web.api.DashboardsApi;
import com.toefly.analytics.adapter.in.web.api.InternalAnalyticsApi;
import com.toefly.analytics.adapter.in.web.api.ProjectionsApi;
import com.toefly.analytics.adapter.in.web.api.StreaksApi;
import com.toefly.analytics.adapter.in.web.dto.ProjectionName;
import com.toefly.common.security.InternalUserAccess;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** analytics-service.yaml keeps the paths relative to the server {@code /api/v1} (docs/05 §6.14). */
@Configuration(proxyBeanMethods = false)
class AnalyticsWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    /** CC|JWT reads of tutor-agent-service (docs/05 §9.1): the user's own dashboard and time report. */
    @Bean
    InternalUserAccess internalUserAccess() {
        return new InternalUserAccess(
                List.of(
                        InternalAnalyticsApi.PATH_GET_STUDENT_DASHBOARD_INTERNAL,
                        InternalAnalyticsApi.PATH_GET_TIME_REPORT_INTERNAL),
                List.of());
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        DashboardsApi.class,
                        StreaksApi.class,
                        ActivityApi.class,
                        AdminMetricsApi.class,
                        ProjectionsApi.class));
    }

    /** Path segments of read models are kebab-case ({@code student-dashboard}). */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ProjectionName.class, ProjectionName::fromValue);
    }
}
