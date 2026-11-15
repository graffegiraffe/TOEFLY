package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.api.CompletionsApi;
import com.toefly.aigateway.adapter.in.web.dto.CompletionRequest;
import com.toefly.aigateway.adapter.in.web.dto.CompletionResponse;
import com.toefly.aigateway.application.port.in.CachePolicy;
import com.toefly.aigateway.application.port.in.CompleteUseCase;
import com.toefly.aigateway.application.port.in.CompletionCommand;
import com.toefly.aigateway.application.port.in.CompletionResult;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /internal/v1/ai/chat/completions}. */
@RestController
public class CompletionController implements CompletionsApi {

    private final CompleteUseCase completions;

    public CompletionController(CompleteUseCase completions) {
        this.completions = completions;
    }

    @Override
    public ResponseEntity<CompletionResponse> completeChat(CompletionRequest request) {
        CompletionResult result = completions.complete(command(request, null));
        return ResponseEntity.ok(response(result));
    }

    static CompletionCommand command(CompletionRequest request, @Nullable UUID correlationId) {
        return new CompletionCommand(
                Callers.service(),
                AiUseCase.valueOf(request.getUseCase().getValue()),
                request.getPromptKey(),
                request.getPromptVersion(),
                request.getVariables(),
                request.getUserId(),
                request.getPiiHints(),
                request.getAllowList(),
                request.getFeature(),
                request.getPriority() == null
                        ? Priority.INTERACTIVE
                        : Priority.valueOf(request.getPriority().getValue()),
                request.getMaxWaitMs(),
                request.getCachePolicy() == null
                        ? CachePolicy.NONE
                        : CachePolicy.valueOf(request.getCachePolicy().getValue()),
                request.getModelOverride(),
                request.getPassTag() == null ? null : request.getPassTag().getValue(),
                correlationId);
    }

    @SuppressWarnings("unchecked")
    static CompletionResponse response(CompletionResult result) {
        return new CompletionResponse()
                .output(result.output() instanceof Map<?, ?> map ? (Map<String, Object>) map : null)
                .text(result.text())
                .aiStamp(AiDtoMapper.stamp(result.aiStamp()))
                .usage(AiDtoMapper.usage(result.usage(), result.latencyMs()))
                .integrityFlags(result.integrityFlags());
    }
}
