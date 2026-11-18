package com.toefly.assessment.adapter.out.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toefly.assessment.application.port.out.DependencyUnavailableException;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.domain.submission.TaskType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Writing tasks from {@code GET /internal/v1/item-sets/{id}} of content-service with the service token, cached for
 * 10 minutes (docs/05-api-contracts.md §9.3.1). The payload follows content/schemas/payload/WRITE_*.schema.json.
 */
public class ContentTaskAdapter implements TaskContentPort {

    static final Duration TTL = Duration.ofMinutes(10);

    private final RestClient content;
    private final Cache<UUID, Optional<TaskContent>> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(500).build();

    public ContentTaskAdapter(RestClient content) {
        this.content = content;
    }

    @Override
    public Optional<TaskContent> load(UUID itemSetId) {
        return cache.get(itemSetId, this::fetch);
    }

    private Optional<TaskContent> fetch(UUID itemSetId) {
        JsonNode set;
        try {
            set = content.get()
                    .uri("/internal/v1/item-sets/{id}", itemSetId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException failure) {
            if (failure.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new DependencyUnavailableException("content-service", failure);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException("content-service", failure);
        }
        if (set == null || !TaskType.supports(set.path("taskType").asString(""))) {
            return Optional.empty();
        }
        String status = set.path("status").asString("PUBLISHED");
        if (!status.equals("PUBLISHED") && !status.equals("APPROVED")) {
            return Optional.empty();
        }
        return Optional.of(task(itemSetId, set));
    }

    public static TaskContent task(UUID itemSetId, JsonNode set) {
        TaskType type = TaskType.valueOf(set.path("taskType").asString());
        JsonNode payload = set.path("payload");
        JsonNode stimulus = payload.path("stimulus");
        JsonNode item = payload.path("items").path(0);
        StringBuilder prompt = new StringBuilder();
        List<String> sources = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int bullets = 0;
        int minWords = stimulus.path("minWords").asInt(0);
        if (type == TaskType.WRITE_ACADEMIC_DISCUSSION) {
            JsonNode professor = stimulus.path("professor");
            String course = stimulus.path("course").asString("");
            prompt.append("Write for an Academic Discussion");
            if (!course.isBlank()) {
                prompt.append(" (").append(course).append(')');
            }
            prompt.append(".\nProfessor ")
                    .append(clean(professor.path("name").asString("")))
                    .append(":\n")
                    .append(clean(professor.path("post").asString("")))
                    .append('\n');
            sources.add(professor.path("post").asString(""));
            names.add(professor.path("name").asString(""));
            for (JsonNode student : stimulus.path("students")) {
                String name = clean(student.path("name").asString(""));
                prompt.append("Student ")
                        .append(name)
                        .append(":\n")
                        .append(clean(student.path("post").asString("")))
                        .append('\n');
                sources.add(student.path("post").asString(""));
                names.add(name);
            }
            if (minWords == 0) {
                minWords = 100;
            }
        } else {
            prompt.append("Write an Email.\nSituation: ")
                    .append(clean(stimulus.path("scenario").asString("")))
                    .append('\n');
            sources.add(stimulus.path("scenario").asString(""));
            String recipient = stimulus.path("recipient").path("name").asString("");
            if (!recipient.isBlank()) {
                prompt.append("To: ").append(clean(recipient)).append('\n');
                names.add(recipient);
            }
            String subject = stimulus.path("subject").asString("");
            if (!subject.isBlank()) {
                prompt.append("Subject: ").append(clean(subject)).append('\n');
            }
            prompt.append("In your email, do the following:\n");
            for (JsonNode bullet : stimulus.path("bullets")) {
                bullets++;
                prompt.append(bullets)
                        .append(". ")
                        .append(clean(bullet.path("text").asString("")))
                        .append('\n');
                sources.add(bullet.path("text").asString(""));
            }
        }
        int limit = stimulus.path("timeLimitSeconds").asInt(type.timeLimitSeconds());
        JsonNode model = item.path("modelAnswers").path(0);
        return new TaskContent(
                itemSetId,
                set.path("version").asInt(1),
                type,
                prompt.toString().strip(),
                sources,
                names.stream().filter(n -> !n.isBlank()).distinct().toList(),
                bullets,
                minWords,
                limit,
                text(model.path("text")),
                text(model.path("source")));
    }

    private static @Nullable String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** Line breaks of PDF extraction inside a sentence become spaces. */
    private static String clean(String text) {
        return text.replaceAll("\\s*\\n\\s*", " ").strip();
    }
}
