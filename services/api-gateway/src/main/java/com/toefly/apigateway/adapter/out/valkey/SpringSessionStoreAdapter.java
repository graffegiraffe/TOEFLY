package com.toefly.apigateway.adapter.out.valkey;

import com.toefly.apigateway.application.port.out.WebSessionStorePort;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Web sessions in Valkey indexed by principal name, which is the Keycloak {@code sub}. */
@Component
public class SpringSessionStoreAdapter implements WebSessionStorePort {

    private final ReactiveFindByIndexNameSessionRepository<? extends Session> index;
    private final ReactiveSessionRepository<? extends Session> sessions;

    public SpringSessionStoreAdapter(
            ReactiveFindByIndexNameSessionRepository<? extends Session> index,
            ReactiveSessionRepository<? extends Session> sessions) {
        this.index = index;
        this.sessions = sessions;
    }

    @Override
    public Mono<Long> deleteAllOf(String userId) {
        return index.findByPrincipalName(userId)
                .flatMapMany(found -> Flux.fromIterable(found.keySet()))
                .flatMap(sessions::deleteById)
                .count();
    }
}
