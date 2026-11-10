package com.toefly.practice.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import com.toefly.practice.application.port.in.AccessDeniedToStudentException;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptNotScoredException;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.in.IdempotencyKeyReuseException;
import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.application.port.in.RequestInProgressException;
import com.toefly.practice.application.port.in.TaskTypeNotSupportedException;
import com.toefly.practice.application.port.in.VersionMismatchException;
import com.toefly.practice.application.port.out.ConcurrencyConflictException;
import com.toefly.practice.application.port.out.DependencyUnavailableException;
import com.toefly.practice.application.port.out.ItemSetUnavailableException;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.Attempts;
import com.toefly.practice.domain.attempt.ExamFormat;
import com.toefly.practice.domain.attempt.Section;
import com.toefly.practice.domain.attempt.TaskType;
import com.toefly.practice.domain.scoring.ItemSetKeys;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PortTypesTest {

    static Stream<RuntimeException> problems() {
        return Stream.of(
                new AccessDeniedToStudentException(),
                new AttemptNotScoredException(AttemptStatus.IN_PROGRESS),
                new IdempotencyKeyReuseException(),
                new RequestInProgressException(),
                new TaskTypeNotSupportedException(TaskType.WRITE_EMAIL),
                new VersionMismatchException(7),
                new ConcurrencyConflictException(UUID.randomUUID(), 3),
                new DependencyUnavailableException("content-service", new IllegalStateException()),
                new ItemSetUnavailableException(UUID.randomUUID()));
    }

    @ParameterizedTest
    @MethodSource("problems")
    @DisplayName("docs/05 §3.2: every error of the ports maps to a catalog type with a message")
    void problemTypes(RuntimeException problem) {
        ProblemType type = problem.getClass().getAnnotation(ProblemType.class);
        assertThat(type).isNotNull();
        assertThat(type.status()).isBetween(400, 599);
        assertThat(problem.getMessage()).isNotBlank();
        if (problem instanceof ProblemProperties properties) {
            assertThat(properties.problemProperties()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("a version mismatch carries the current version for the ETag")
    void versionMismatch() {
        assertThat(new VersionMismatchException(7).currentVersion()).isEqualTo(7);
    }

    @Test
    @DisplayName("callers, queries and details expose what the controllers need")
    void readModels() {
        UUID user = UUID.randomUUID();
        Caller caller = new Caller(user, Set.of(Caller.Role.TUTOR));
        assertThat(caller.is(Caller.Role.TUTOR)).isTrue();
        assertThat(caller.is(Caller.Role.STUDENT)).isFalse();

        AttemptQuery query = new AttemptQuery(user, null, null, null, Set.of(), Section.READING, null, null, true);
        UUID other = UUID.randomUUID();
        assertThat(query.forUser(other).userId()).isEqualTo(other);
        assertThat(query.forUser(other).section()).isEqualTo(Section.READING);

        ItemSetContent content = new ItemSetContent(
                UUID.randomUUID(),
                1,
                TaskType.READ_ACADEMIC_PASSAGE,
                Section.READING,
                ExamFormat.TOEFL_2026,
                "APPROVED",
                360,
                Map.of("title", "x"),
                Map.of("title", "x", "transcript", List.of()),
                Map.of(),
                Map.of(),
                new ItemSetKeys(Map.of(), Map.of()));
        AttemptDetails open = new AttemptDetails(Attempts.practice(1).snapshot(), content, 0);
        assertThat(open.visibleStimulus()).doesNotContainKey("transcript");
    }
}
