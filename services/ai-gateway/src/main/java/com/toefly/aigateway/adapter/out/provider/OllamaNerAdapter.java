package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.NerPort;
import com.toefly.aigateway.domain.safety.PiiSpan;
import com.toefly.aigateway.domain.safety.PiiStage;
import com.toefly.aigateway.domain.safety.PiiType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stage 3 of masking on the local model {@code pii} (docs/12, phase 5a, decision 2): the model lists people, places
 * and organisations as JSON; the adapter finds every occurrence. Countries and big cities stay; an organisation is
 * masked only next to words of a workplace or a school.
 */
public class OllamaNerAdapter implements NerPort {

    static final String SYSTEM = """
            You find personal data in a text written by a student. List real people's names, street addresses or \
            small towns and villages, and names of the student's employer, school or university. Do not list \
            countries, capitals or famous places. Copy each entity exactly as written in the text.
            Answer with JSON only: {"entities":[{"text":"...","type":"PERSON|PLACE|ORG"}]}. \
            If there are none, answer {"entities":[]}.""";
    private static final Pattern ORG_CONTEXT = Pattern.compile(
            "(work(s|ed|ing)?\\s+(at|for)|employer|company|school|university|college|institute|работа|университет|школ)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OllamaChatModel chat;
    private final String model;

    public OllamaNerAdapter(OllamaChatModel chat, String model) {
        this.chat = chat;
        this.model = model;
    }

    @Override
    public List<PiiSpan> find(String text) {
        String answer;
        try {
            ChatResponse response = chat.call(new Prompt(
                    List.of(new SystemMessage(SYSTEM), new UserMessage(text)),
                    OllamaChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .format("json")
                            .build()));
            answer = response.getResult() == null
                    ? ""
                    : response.getResult().getOutput().getText();
        } catch (RuntimeException error) {
            throw ProviderErrors.map("OLLAMA", error);
        }
        return spans(text, answer == null ? "" : answer);
    }

    static List<PiiSpan> spans(String text, String answer) {
        List<PiiSpan> spans = new ArrayList<>();
        JsonNode entities;
        try {
            entities = JSON.readTree(answer).path("entities");
        } catch (RuntimeException notJson) {
            return spans;
        }
        for (JsonNode entity : entities) {
            String value = entity.path("text").asString("").strip();
            PiiType type = type(entity.path("type").asString(""));
            if (value.length() < 2 || type == null) {
                continue;
            }
            if (type == PiiType.PLACE && GeoAllowList.allowed(value)) {
                continue;
            }
            Matcher matcher = Pattern.compile(
                            "(?<![\\p{L}\\p{N}])" + Pattern.quote(value) + "(?![\\p{L}\\p{N}])",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(text);
            while (matcher.find()) {
                if (type == PiiType.ORG && !nearWorkplace(text, matcher.start())) {
                    continue;
                }
                spans.add(new PiiSpan(
                        matcher.start(),
                        matcher.end(),
                        type,
                        PiiStage.NER,
                        type.name() + ":" + value.toLowerCase(Locale.ROOT)));
            }
        }
        return spans;
    }

    private static boolean nearWorkplace(String text, int start) {
        String before = text.substring(Math.max(0, start - 40), start);
        return ORG_CONTEXT.matcher(before).find();
    }

    private static PiiType type(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "PERSON", "PER" -> PiiType.PERSON;
            case "PLACE", "LOC", "LOCATION", "ADDRESS" -> PiiType.PLACE;
            case "ORG", "ORGANIZATION", "ORGANISATION" -> PiiType.ORG;
            default -> null;
        };
    }
}
