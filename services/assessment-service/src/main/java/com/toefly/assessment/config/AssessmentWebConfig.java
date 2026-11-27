package com.toefly.assessment.config;

import com.toefly.assessment.adapter.in.web.api.AdminAssessmentApi;
import com.toefly.assessment.adapter.in.web.api.BandEstimatesApi;
import com.toefly.assessment.adapter.in.web.api.EvaluationsApi;
import com.toefly.assessment.adapter.in.web.api.InternalAssessmentApi;
import com.toefly.assessment.adapter.in.web.api.SubmissionsApi;
import com.toefly.common.security.InternalUserAccess;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** assessment-service.yaml keeps the paths relative to the server {@code /api/v1} (docs/05 §6.5). */
@Configuration(proxyBeanMethods = false)
class AssessmentWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    /** CC|JWT reads of tutor-agent-service (docs/05 §9.1): the user's own estimate, a submission checked by owner. */
    @Bean
    InternalUserAccess internalUserAccess() {
        return new InternalUserAccess(
                List.of(InternalAssessmentApi.PATH_GET_BAND_ESTIMATE_INTERNAL),
                List.of(InternalAssessmentApi.PATH_GET_SUBMISSION_INTERNAL));
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        SubmissionsApi.class, EvaluationsApi.class, BandEstimatesApi.class, AdminAssessmentApi.class));
    }
}
