package com.toefly.tutoragent.adapter.in.web;

import com.toefly.tutoragent.adapter.in.web.api.AdminAgentApi;
import com.toefly.tutoragent.adapter.in.web.api.InterviewSessionsApi;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Operations of later phases answer 501 (docs/12, phase 5c, decision 6). */
@RestController
class LaterPhasesController implements InterviewSessionsApi, AdminAgentApi {

    @Override
    public ResponseEntity<Void> startInterviewSession(Map<String, Object> body) {
        throw ApiException.notImplemented("6");
    }

    @Override
    public ResponseEntity<Void> getInterviewSession(UUID sessionId) {
        throw ApiException.notImplemented("6");
    }

    @Override
    public ResponseEntity<Void> listAgentRuns() {
        throw ApiException.notImplemented("15");
    }
}
