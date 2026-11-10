package com.toefly.practice.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.practice.adapter.in.web.IdempotentExecution.Outcome;
import com.toefly.practice.adapter.in.web.api.AttemptsApi;
import com.toefly.practice.adapter.in.web.dto.AnswerState;
import com.toefly.practice.adapter.in.web.dto.Attempt;
import com.toefly.practice.adapter.in.web.dto.AttemptMode;
import com.toefly.practice.adapter.in.web.dto.AttemptReview;
import com.toefly.practice.adapter.in.web.dto.AttemptStatus;
import com.toefly.practice.adapter.in.web.dto.AttemptSummaryPage;
import com.toefly.practice.adapter.in.web.dto.HeartbeatRequest;
import com.toefly.practice.adapter.in.web.dto.PageInfo;
import com.toefly.practice.adapter.in.web.dto.RecordAnswerRequest;
import com.toefly.practice.adapter.in.web.dto.Section;
import com.toefly.practice.adapter.in.web.dto.StartAttemptRequest;
import com.toefly.practice.adapter.in.web.dto.SubmitAttemptRequest;
import com.toefly.practice.application.port.in.AbandonAttemptUseCase;
import com.toefly.practice.application.port.in.AttemptCursor;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.in.GetAttemptUseCase;
import com.toefly.practice.application.port.in.RecordAnswerUseCase;
import com.toefly.practice.application.port.in.RecordHeartbeatUseCase;
import com.toefly.practice.application.port.in.StartAttemptUseCase;
import com.toefly.practice.application.port.in.SubmitAttemptUseCase;
import com.toefly.practice.application.port.in.SubmitAttemptUseCase.FinalAnswer;
import com.toefly.practice.domain.attempt.AnswerFormException;
import com.toefly.practice.domain.attempt.SubmitReason;
import com.toefly.practice.domain.time.HeartbeatState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Attempts of the learner (docs/05-api-contracts.md §6.4, §7.1). */
@RestController
public class AttemptController implements AttemptsApi {

    private final StartAttemptUseCase start;
    private final RecordAnswerUseCase answers;
    private final SubmitAttemptUseCase submit;
    private final AbandonAttemptUseCase abandon;
    private final GetAttemptUseCase attempts;
    private final RecordHeartbeatUseCase heartbeats;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;

    public AttemptController(
            StartAttemptUseCase start,
            RecordAnswerUseCase answers,
            SubmitAttemptUseCase submit,
            AbandonAttemptUseCase abandon,
            GetAttemptUseCase attempts,
            RecordHeartbeatUseCase heartbeats,
            CurrentCaller caller,
            IdempotentExecution idempotent) {
        this.start = start;
        this.answers = answers;
        this.submit = submit;
        this.abandon = abandon;
        this.attempts = attempts;
        this.heartbeats = heartbeats;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Attempt> startAttempt(UUID idempotencyKey, StartAttemptRequest request) {
        if (request.getMode() == AttemptMode.EXAM) {
            throw new ExamViaSessionException();
        }
        UUID userId = caller.userId();
        return idempotent.run(userId, idempotencyKey, "startAttempt", request, Attempt.class, () -> {
            AttemptDetails details = start.start(new StartAttemptUseCase.StartPractice(
                    userId, request.getItemSetId(), request.getWeekTaskId(), request.getTimeZone(), UUID.randomUUID()));
            return new Outcome<>(
                    HttpStatus.CREATED,
                    AttemptDtoMapper.attempt(details),
                    "/api/v1/attempts/" + details.attempt().attemptId(),
                    ETags.of(details.attempt().version()));
        });
    }

    @Override
    @PreAuthorize("hasAnyRole('STUDENT', 'TUTOR')")
    public ResponseEntity<AttemptSummaryPage> listAttempts(
            @Nullable UUID studentId,
            @Nullable UUID itemSetId,
            @Nullable UUID weekTaskId,
            @Nullable AttemptMode mode,
            @Nullable List<AttemptStatus> status,
            @Nullable Section section,
            @Nullable OffsetDateTime from,
            @Nullable OffsetDateTime to,
            String sort,
            Integer limit,
            @Nullable String cursor) {
        Caller who = caller.get();
        int size = CursorCodec.limit(limit);
        Set<com.toefly.practice.domain.attempt.AttemptStatus> statuses =
                EnumSet.noneOf(com.toefly.practice.domain.attempt.AttemptStatus.class);
        if (status != null) {
            status.forEach(
                    value -> statuses.add(com.toefly.practice.domain.attempt.AttemptStatus.valueOf(value.name())));
        }
        AttemptQuery query = new AttemptQuery(
                who.userId(),
                itemSetId,
                weekTaskId,
                mode == null ? null : com.toefly.practice.domain.attempt.AttemptMode.valueOf(mode.name()),
                statuses,
                section == null ? null : com.toefly.practice.domain.attempt.Section.valueOf(section.name()),
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(),
                "startedAt".equals(sort));
        String fingerprint = String.join(
                "|",
                String.valueOf(studentId),
                String.valueOf(itemSetId),
                String.valueOf(weekTaskId),
                String.valueOf(mode),
                statuses.stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                String.valueOf(section),
                String.valueOf(from),
                String.valueOf(to),
                sort);
        AttemptCursor after = null;
        if (cursor != null) {
            List<String> values = CursorCodec.decode(cursor, fingerprint);
            after = new AttemptCursor(Instant.parse(values.get(0)), UUID.fromString(values.get(1)));
        }
        List<AttemptView> rows = attempts.list(who, studentId, query, after, size + 1);
        boolean more = rows.size() > size;
        List<AttemptView> page = more ? rows.subList(0, size) : rows;
        String next = more
                ? CursorCodec.encode(
                        fingerprint,
                        List.of(
                                page.getLast().startedAt().toString(),
                                page.getLast().attemptId().toString()))
                : null;
        return ResponseEntity.ok(new AttemptSummaryPage()
                .items(page.stream().map(AttemptDtoMapper::summary).toList())
                .page(new PageInfo().nextCursor(next).hasMore(more).limit(size)));
    }

    @Override
    @PreAuthorize("hasAnyRole('STUDENT', 'TUTOR')")
    public ResponseEntity<Attempt> getAttempt(UUID attemptId, @Nullable String ifNoneMatch) {
        AttemptDetails details = attempts.get(caller.get(), attemptId);
        String etag = ETags.of(details.attempt().version());
        if (ETags.matches(ifNoneMatch, details.attempt().version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(AttemptDtoMapper.attempt(details));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AnswerState> recordAnswer(
            String ifMatch, UUID attemptId, UUID itemId, RecordAnswerRequest request) {
        RecordAnswerUseCase.AnswerResult result = answers.record(new RecordAnswerUseCase.RecordAnswer(
                caller.userId(),
                attemptId,
                itemId,
                AttemptDtoMapper.answer(request.getAnswer()),
                requireVersion(ifMatch),
                request.getClientTimestamp() == null
                        ? null
                        : request.getClientTimestamp().toInstant(),
                request.getAnsweredInMs() == null
                        ? null
                        : request.getAnsweredInMs().longValue()));
        return ResponseEntity.ok().eTag(ETags.of(result.attemptVersion())).body(answerState(result));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> clearAnswer(String ifMatch, UUID attemptId, UUID itemId) {
        RecordAnswerUseCase.AnswerResult result = answers.clear(
                new RecordAnswerUseCase.ClearAnswer(caller.userId(), attemptId, itemId, requireVersion(ifMatch)));
        return ResponseEntity.noContent()
                .eTag(ETags.of(result.attemptVersion()))
                .build();
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> sendAttemptHeartbeat(UUID attemptId, HeartbeatRequest request) {
        heartbeats.attemptHeartbeat(new RecordHeartbeatUseCase.AttemptHeartbeat(
                caller.userId(),
                attemptId,
                request.getSequence(),
                HeartbeatState.valueOf(request.getState().getValue()),
                request.getActiveSecondsDelta(),
                request.getAudioReplays(),
                UUID.randomUUID()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptReview> submitAttempt(
            UUID idempotencyKey, UUID attemptId, @Nullable SubmitAttemptRequest request) {
        UUID userId = caller.userId();
        return idempotent.run(
                userId, idempotencyKey, "submitAttempt:" + attemptId, request, AttemptReview.class, () -> {
                    AttemptReviewResult review = submit.submit(new SubmitAttemptUseCase.Submit(
                            userId, attemptId, finalAnswers(request), reason(request), UUID.randomUUID()));
                    return Outcome.ok(
                            AttemptDtoMapper.review(review),
                            ETags.of(review.attempt().version()));
                });
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Attempt> abandonAttempt(UUID idempotencyKey, UUID attemptId) {
        UUID userId = caller.userId();
        return idempotent.run(userId, idempotencyKey, "abandonAttempt:" + attemptId, null, Attempt.class, () -> {
            AttemptDetails details = abandon.abandon(userId, attemptId, UUID.randomUUID());
            return Outcome.ok(
                    AttemptDtoMapper.attempt(details),
                    ETags.of(details.attempt().version()));
        });
    }

    @Override
    @PreAuthorize("hasAnyRole('STUDENT', 'TUTOR')")
    public ResponseEntity<AttemptReview> getAttemptReview(UUID attemptId, @Nullable String ifNoneMatch) {
        AttemptReviewResult review = attempts.review(caller.get(), attemptId);
        String etag = ETags.of(review.attempt().version());
        if (ETags.matches(ifNoneMatch, review.attempt().version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(AttemptDtoMapper.review(review));
    }

    static List<FinalAnswer> finalAnswers(@Nullable SubmitAttemptRequest request) {
        if (request == null) {
            return List.of();
        }
        return request.getFinalAnswers().stream()
                .map(answer -> new FinalAnswer(answer.getItemId(), AttemptDtoMapper.answer(answer.getAnswer())))
                .toList();
    }

    static SubmitReason reason(@Nullable SubmitAttemptRequest request) {
        return request != null && request.getReason() == SubmitAttemptRequest.ReasonEnum.TIMEOUT
                ? SubmitReason.TIMEOUT
                : SubmitReason.USER;
    }

    static AnswerState answerState(RecordAnswerUseCase.AnswerResult result) {
        return new AnswerState()
                .itemId(result.itemId())
                .answered(result.answer() != null)
                .answer(AttemptDtoMapper.answer(result.answer()))
                .attemptVersion(result.attemptVersion())
                .recordedAt(AttemptDtoMapper.time(result.recordedAt()))
                .deadlineAt(AttemptDtoMapper.time(result.deadlineAt()));
    }

    private static int requireVersion(String ifMatch) {
        Integer version = ETags.version(ifMatch);
        if (version == null) {
            throw new AnswerFormException("If-Match must be the ETag of the attempt, for example \"7\".");
        }
        return version;
    }
}
