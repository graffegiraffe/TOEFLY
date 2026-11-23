package com.toefly.tutoragent.config;

import com.toefly.tutoragent.adapter.in.web.api.AdminAgentApi;
import com.toefly.tutoragent.adapter.in.web.api.ChatStreamsApi;
import com.toefly.tutoragent.adapter.in.web.api.ChatThreadsApi;
import com.toefly.tutoragent.adapter.in.web.api.InterviewSessionsApi;
import com.toefly.tutoragent.adapter.in.web.api.LearnerModelApi;
import com.toefly.tutoragent.adapter.in.web.api.PendingActionsApi;
import com.toefly.tutoragent.adapter.in.web.api.WeeklyReviewsApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** tutor-agent-service.yaml keeps the paths relative to the server {@code /api/v1} (docs/05 §6.8). */
@Configuration(proxyBeanMethods = false)
class TutorAgentWebConfig implements WebMvcConfigurer {

    static final String PUBLIC_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                PUBLIC_PREFIX,
                HandlerTypePredicate.forAssignableType(
                        ChatThreadsApi.class,
                        ChatStreamsApi.class,
                        PendingActionsApi.class,
                        WeeklyReviewsApi.class,
                        LearnerModelApi.class,
                        InterviewSessionsApi.class,
                        AdminAgentApi.class));
    }
}
