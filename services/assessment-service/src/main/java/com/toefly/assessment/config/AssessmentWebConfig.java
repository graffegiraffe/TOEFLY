package com.toefly.assessment.config;

import com.toefly.assessment.adapter.in.web.api.AdminAssessmentApi;
import com.toefly.assessment.adapter.in.web.api.BandEstimatesApi;
import com.toefly.assessment.adapter.in.web.api.EvaluationsApi;
import com.toefly.assessment.adapter.in.web.api.SubmissionsApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** assessment-service.yaml keeps the paths relative to the server {@code /api/v1} (docs/05 §6.5). */
@Configuration(proxyBeanMethods = false)
class AssessmentWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        SubmissionsApi.class, EvaluationsApi.class, BandEstimatesApi.class, AdminAssessmentApi.class));
    }
}
