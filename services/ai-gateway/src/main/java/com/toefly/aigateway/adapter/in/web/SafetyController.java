package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.api.EmbeddingsApi;
import com.toefly.aigateway.adapter.in.web.api.ModerationsApi;
import com.toefly.aigateway.adapter.in.web.dto.EmbeddingRequest;
import com.toefly.aigateway.adapter.in.web.dto.EmbeddingResponse;
import com.toefly.aigateway.adapter.in.web.dto.GuardVerdict;
import com.toefly.aigateway.adapter.in.web.dto.ModerationRequest;
import com.toefly.aigateway.adapter.in.web.dto.ModerationResult;
import com.toefly.aigateway.application.port.in.EmbedUseCase;
import com.toefly.aigateway.application.port.in.ModerateUseCase;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import com.toefly.aigateway.domain.safety.HeuristicHit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Local-only operations: moderation by the guard and embeddings. */
@RestController
public class SafetyController implements ModerationsApi, EmbeddingsApi {

    private final ModerateUseCase moderation;
    private final EmbedUseCase embeddings;

    public SafetyController(ModerateUseCase moderation, EmbedUseCase embeddings) {
        this.moderation = moderation;
        this.embeddings = embeddings;
    }

    @Override
    public ResponseEntity<ModerationResult> moderate(ModerationRequest request) {
        GuardPolicy policy = request.getPolicy() == null
                ? GuardPolicy.CHAT
                : GuardPolicy.valueOf(request.getPolicy().getValue());
        GuardAssessment assessment = moderation.moderate(request.getText(), policy);
        return ResponseEntity.ok(new ModerationResult()
                .allowed(assessment.allowed())
                .verdict(GuardVerdict.fromValue(assessment.verdict().name()))
                .categories(assessment.categories())
                .heuristicHits(assessment.heuristicHits().stream()
                        .map(HeuristicHit::name)
                        .toList())
                .model(assessment.model())
                .modelAvailable(assessment.modelAvailable())
                .latencyMs(assessment.latencyMs())
                .integrityFlags(assessment.integrityFlags()));
    }

    @Override
    public ResponseEntity<EmbeddingResponse> embed(EmbeddingRequest request) {
        var result = embeddings.embed(Callers.service(), request.getTexts(), request.getUserId());
        List<List<Float>> vectors = new ArrayList<>();
        for (float[] vector : result.vectors()) {
            List<Float> values = new ArrayList<>(vector.length);
            for (float value : vector) {
                values.add(value);
            }
            vectors.add(values);
        }
        return ResponseEntity.ok(new EmbeddingResponse()
                .vectors(vectors)
                .dimensions(result.dimensions())
                .model(result.model()));
    }
}
