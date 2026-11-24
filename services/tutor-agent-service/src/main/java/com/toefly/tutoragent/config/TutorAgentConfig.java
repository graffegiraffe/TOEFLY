package com.toefly.tutoragent.config;

import com.toefly.common.security.ClientCredentialsInterceptor;
import com.toefly.tutoragent.adapter.out.client.AiGatewayAdapter;
import com.toefly.tutoragent.adapter.out.client.AssessmentClientAdapter;
import com.toefly.tutoragent.adapter.out.client.GatewayChatModelAdapter;
import com.toefly.tutoragent.adapter.out.client.OwnerToolsAdapter;
import com.toefly.tutoragent.adapter.out.client.PracticeClientAdapter;
import com.toefly.tutoragent.adapter.out.client.PromptClientAdapter;
import com.toefly.tutoragent.adapter.out.client.ServiceToken;
import com.toefly.tutoragent.application.port.in.ChatUseCase;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.MemoryUseCase;
import com.toefly.tutoragent.application.port.in.ProjectionUseCase;
import com.toefly.tutoragent.application.port.in.ReengagementUseCase;
import com.toefly.tutoragent.application.port.in.WeeklyReviewUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.ChatModelPort;
import com.toefly.tutoragent.application.port.out.ChatRepository;
import com.toefly.tutoragent.application.port.out.DeferredReviewRepository;
import com.toefly.tutoragent.application.port.out.EntitlementPort;
import com.toefly.tutoragent.application.port.out.LearnerEventPublisherPort;
import com.toefly.tutoragent.application.port.out.LearnerModelRepository;
import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PendingActionRepository;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.ProjectionRepository;
import com.toefly.tutoragent.application.port.out.PromptPort;
import com.toefly.tutoragent.application.port.out.PublicationSchedule;
import com.toefly.tutoragent.application.port.out.ReengagementPublisherPort;
import com.toefly.tutoragent.application.port.out.ReengagementRepository;
import com.toefly.tutoragent.application.port.out.ReviewEventPublisherPort;
import com.toefly.tutoragent.application.port.out.ToolLog;
import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.application.service.ChatService;
import com.toefly.tutoragent.application.service.LearnerModelService;
import com.toefly.tutoragent.application.service.MemoryService;
import com.toefly.tutoragent.application.service.ProjectionService;
import com.toefly.tutoragent.application.service.ReengagementService;
import com.toefly.tutoragent.application.service.ScreenContextAssembler;
import com.toefly.tutoragent.application.service.ToolExecutor;
import com.toefly.tutoragent.application.service.Transactions;
import com.toefly.tutoragent.application.service.WeeklyReviewService;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Wiring of tutor-agent-service: owner clients with client credentials, the learner model and weekly reviews. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(TutorAgentProperties.class)
class TutorAgentConfig {

    static final String REGISTRATION = "svc-tutor-agent-service";

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Client credentials for calls without a learner token (docs/10-security-privacy.md §7.2). */
    @Bean
    OAuth2AuthorizedClientManager serviceClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    Transactions transactions(TransactionTemplate template) {
        return new Transactions() {
            @Override
            public void run(Runnable work) {
                template.executeWithoutResult(status -> work.run());
            }

            @Override
            public <T> T call(Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }

    @Bean
    Taxonomy taxonomy(JsonMapper json) {
        return TaxonomyLoader.load(json);
    }

    @Bean
    AiPort aiPort(RestClient.Builder builder, OAuth2AuthorizedClientManager clients, TutorAgentProperties p) {
        return new AiGatewayAdapter(client(builder, clients, p.aiGatewayUrl(), p.aiReadTimeout()));
    }

    @Bean
    PracticePort practicePort(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, TutorAgentProperties p) {
        return new PracticeClientAdapter(client(builder, clients, p.practiceUrl(), p.toolReadTimeout()));
    }

    @Bean
    AssessmentPort assessmentPort(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, TutorAgentProperties p) {
        return new AssessmentClientAdapter(client(builder, clients, p.assessmentUrl(), p.toolReadTimeout()));
    }

    @Bean
    LearnerModelService learnerModelService(
            LearnerModelRepository models,
            MistakeRepository mistakes,
            MistakeMemoryRepository memory,
            PublicationSchedule publications,
            LearnerEventPublisherPort events,
            PracticeResultRepository results,
            PracticePort practice,
            AssessmentPort assessment,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock) {
        return new LearnerModelService(
                models,
                mistakes,
                memory,
                publications,
                events,
                results,
                practice,
                assessment,
                taxonomy,
                transactions,
                clock);
    }

    @Bean
    WeeklyReviewUseCase weeklyReviewService(
            LearnerModelUseCase learner,
            LearnerModelRepository models,
            MistakeRepository mistakes,
            PracticeResultRepository results,
            WeeklyReviewRepository reviews,
            DeferredReviewRepository deferred,
            EntitlementPort entitlements,
            AiPort ai,
            ReviewEventPublisherPort events,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock) {
        return new WeeklyReviewService(
                learner,
                models,
                mistakes,
                results,
                reviews,
                deferred,
                entitlements,
                ai,
                events,
                taxonomy,
                transactions,
                clock);
    }

    @Bean
    ChatModelPort chatModelPort(OAuth2AuthorizedClientManager clients, TutorAgentProperties p) {
        return new GatewayChatModelAdapter(
                p.aiGatewayUrl(), new ServiceToken(clients, REGISTRATION), Duration.ofSeconds(180));
    }

    @Bean
    PromptPort promptPort(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, TutorAgentProperties p, Clock clock) {
        return new PromptClientAdapter(client(builder, clients, p.aiGatewayUrl(), Duration.ofSeconds(5)), clock);
    }

    @Bean
    OwnerToolsPort ownerToolsPort(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, TutorAgentProperties p) {
        return new OwnerToolsAdapter(
                client(builder, clients, p.planningUrl(), p.toolReadTimeout()),
                client(builder, clients, p.analyticsUrl(), p.toolReadTimeout()),
                client(builder, clients, p.contentUrl(), p.toolReadTimeout()));
    }

    @Bean
    ToolExecutor toolExecutor(
            OwnerToolsPort owners,
            PracticeResultRepository results,
            PracticePort practice,
            AssessmentPort assessment,
            LearnerModelUseCase learner,
            MistakeRepository mistakes,
            MistakeMemoryRepository memory,
            WeeklyReviewRepository reviews,
            AiPort ai,
            Taxonomy taxonomy,
            Clock clock) {
        return new ToolExecutor(
                owners, results, practice, assessment, learner, mistakes, memory, reviews, ai, taxonomy, clock);
    }

    @Bean
    ChatUseCase chatService(
            ChatRepository chats,
            PendingActionRepository actions,
            ToolLog toolLog,
            EntitlementPort entitlements,
            ProjectionRepository projections,
            LearnerModelUseCase learner,
            AiPort ai,
            ChatModelPort model,
            PromptPort prompts,
            OwnerToolsPort owners,
            ToolExecutor tools,
            PracticeResultRepository results,
            PracticePort practice,
            AssessmentPort assessment,
            WeeklyReviewRepository reviews,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock,
            JsonMapper json) {
        return new ChatService(
                chats,
                actions,
                toolLog,
                entitlements,
                projections,
                learner,
                ai,
                model,
                prompts,
                owners,
                tools,
                new ScreenContextAssembler(practice, results, assessment, owners, reviews),
                ToolSpecsLoader.load(json),
                taxonomy,
                transactions,
                clock);
    }

    @Bean
    ReengagementUseCase reengagementService(
            ReengagementRepository repository,
            ReengagementPublisherPort events,
            LearnerModelUseCase learner,
            PracticeResultRepository results,
            OwnerToolsPort owners,
            AiPort ai,
            Transactions transactions,
            Clock clock) {
        return new ReengagementService(repository, events, learner, results, owners, ai, transactions, clock);
    }

    @Bean
    ProjectionUseCase projectionService(ProjectionRepository projections) {
        return new ProjectionService(projections);
    }

    @Bean
    MemoryUseCase memoryService(MistakeMemoryRepository memory, AiPort ai) {
        return new MemoryService(memory, ai);
    }

    static RestClient client(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, String baseUrl, Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1000));
        factory.setReadTimeout(read);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new ClientCredentialsInterceptor(clients, REGISTRATION))
                .build();
    }
}
