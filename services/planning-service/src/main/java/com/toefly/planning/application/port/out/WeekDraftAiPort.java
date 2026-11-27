package com.toefly.planning.application.port.out;

import com.toefly.planning.domain.composition.DraftLayout;
import com.toefly.planning.domain.week.AiStamp;
import java.util.Map;
import java.util.UUID;

/**
 * ai-gateway for WeekGenerator: {@code POST /internal/v1/ai/chat/completions} with the use case
 * {@code WEEK_GENERATION} and the prompt {@code planning.week.generate} (docs/06-ai-agent.md §5.2).
 */
public interface WeekDraftAiPort {

    String PROMPT_KEY = "planning.week.generate";

    /**
     * One call of the model.
     *
     * @throws AiUnavailableException when ai-gateway gives no structured answer
     */
    DraftReply draft(DraftCall call);

    /**
     * @param variables the placeholders of the prompt
     * @param idempotencyKey {@code WeekGenerator:<weekId>:<reviewId>:<attempt>} (docs/06 §1.3)
     */
    record DraftCall(UUID userId, Map<String, Object> variables, String idempotencyKey) {

        public DraftCall {
            variables = Map.copyOf(variables);
        }
    }

    record DraftReply(DraftLayout layout, AiStamp stamp) {}
}
