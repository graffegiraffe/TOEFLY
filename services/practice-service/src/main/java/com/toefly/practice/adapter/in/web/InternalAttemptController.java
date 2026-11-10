package com.toefly.practice.adapter.in.web;

import com.toefly.practice.adapter.in.web.IdempotentExecution.Outcome;
import com.toefly.practice.adapter.in.web.api.InternalAttemptsApi;
import com.toefly.practice.adapter.in.web.dto.AbilityList;
import com.toefly.practice.adapter.in.web.dto.AnswerState;
import com.toefly.practice.adapter.in.web.dto.Attempt;
import com.toefly.practice.adapter.in.web.dto.AttemptReview;
import com.toefly.practice.adapter.in.web.dto.AttemptScoreSummary;
import com.toefly.practice.adapter.in.web.dto.AttemptScoreSummaryItemsInner;
import com.toefly.practice.adapter.in.web.dto.AttemptSummaryPage;
import com.toefly.practice.adapter.in.web.dto.PageInfo;
import com.toefly.practice.adapter.in.web.dto.PracticeUserDataExport;
import com.toefly.practice.adapter.in.web.dto.PracticeUserDataExportTimeEntriesInner;
import com.toefly.practice.adapter.in.web.dto.RecordAnswerRequest;
import com.toefly.practice.adapter.in.web.dto.Score;
import com.toefly.practice.adapter.in.web.dto.Section;
import com.toefly.practice.adapter.in.web.dto.StartExamAttemptRequest;
import com.toefly.practice.adapter.in.web.dto.SubmitAttemptRequest;
import com.toefly.practice.adapter.in.web.dto.WeekTaskKind;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.GetAbilityUseCase;
import com.toefly.practice.application.port.in.GetAttemptUseCase;
import com.toefly.practice.application.port.in.RecordAnswerUseCase;
import com.toefly.practice.application.port.in.StartAttemptUseCase;
import com.toefly.practice.application.port.in.SubmitAttemptUseCase;
import com.toefly.practice.application.port.in.UserDataUseCase;
import com.toefly.practice.domain.time.ContextType;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API (/internal/v1, scope internal): EXAM attempts for mockexam-service, reads for tutor-agent-service and
 * the GDPR export for profile-service (docs/05-api-contracts.md §9.3.2).
 */
@RestController
public class InternalAttemptController implements InternalAttemptsApi {

    private final StartAttemptUseCase start;
    private final RecordAnswerUseCase answers;
    private final SubmitAttemptUseCase submit;
    private final GetAttemptUseCase attempts;
    private final GetAbilityUseCase abilities;
    private final UserDataUseCase userData;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;

    public InternalAttemptController(
            StartAttemptUseCase start,
            RecordAnswerUseCase answers,
            SubmitAttemptUseCase submit,
            GetAttemptUseCase attempts,
            GetAbilityUseCase abilities,
            UserDataUseCase userData,
            CurrentCaller caller,
            IdempotentExecution idempotent) {
        this.start = start;
        this.answers = answers;
        this.submit = submit;
        this.attempts = attempts;
        this.abilities = abilities;
        this.userData = userData;
        this.caller = caller;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<Attempt> startExamAttempt(UUID idempotencyKey, StartExamAttemptRequest request) {
        return idempotent.run(caller.userId(), idempotencyKey, "startExamAttempt", request, Attempt.class, () -> {
            AttemptDetails details = start.startExam(new StartAttemptUseCase.StartExam(
                    request.getUserId(),
                    request.getItemSetId(),
                    request.getExamSessionId(),
                    request.getModuleId(),
                    request.getDeadlineAt().toInstant(),
                    request.getTimingMultiplier() == null
                            ? 1.0
                            : request.getTimingMultiplier().getValue().doubleValue(),
                    UUID.randomUUID()));
            return new Outcome<>(
                    HttpStatus.CREATED,
                    AttemptDtoMapper.attempt(details),
                    "/internal/v1/attempts/" + details.attempt().attemptId(),
                    ETags.of(details.attempt().version()));
        });
    }

    @Override
    public ResponseEntity<AnswerState> recordExamAnswer(
            UUID attemptId, UUID itemId, RecordAnswerRequest request, @Nullable String ifMatch) {
        RecordAnswerUseCase.AnswerResult result = answers.record(new RecordAnswerUseCase.RecordAnswer(
                null,
                attemptId,
                itemId,
                AttemptDtoMapper.answer(request.getAnswer()),
                ETags.version(ifMatch),
                request.getClientTimestamp() == null
                        ? null
                        : request.getClientTimestamp().toInstant(),
                request.getAnsweredInMs() == null
                        ? null
                        : request.getAnsweredInMs().longValue()));
        return ResponseEntity.ok().eTag(ETags.of(result.attemptVersion())).body(AttemptController.answerState(result));
    }

    @Override
    public ResponseEntity<AttemptScoreSummary> submitExamAttempt(
            UUID idempotencyKey, UUID attemptId, @Nullable SubmitAttemptRequest request) {
        return idempotent.run(
                caller.userId(),
                idempotencyKey,
                "submitExamAttempt:" + attemptId,
                request,
                AttemptScoreSummary.class,
                () -> {
                    AttemptReviewResult review = submit.submit(new SubmitAttemptUseCase.Submit(
                            null,
                            attemptId,
                            AttemptController.finalAnswers(request),
                            AttemptController.reason(request),
                            UUID.randomUUID()));
                    return Outcome.ok(
                            new AttemptScoreSummary()
                                    .attemptId(attemptId)
                                    .status(AttemptDtoMapper.status(
                                            review.attempt().status()))
                                    .score(new Score()
                                            .correct(review.attempt().correctCount())
                                            .total(review.items().size())
                                            .unanswered(review.attempt().unansweredCount()))
                                    .items(review.items().stream()
                                            .map(item -> new AttemptScoreSummaryItemsInner()
                                                    .itemId(item.itemId())
                                                    .correct(item.correct()))
                                            .toList()),
                            null);
                });
    }

    @Override
    public ResponseEntity<AttemptSummaryPage> listUserAttemptsInternal(
            UUID userId,
            @Nullable Section section,
            @Nullable OffsetDateTime from,
            @Nullable OffsetDateTime to,
            Integer limit) {
        AttemptQuery query = new AttemptQuery(
                userId,
                null,
                null,
                null,
                Set.of(),
                section == null ? null : com.toefly.practice.domain.attempt.Section.valueOf(section.name()),
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(),
                false);
        return ResponseEntity.ok(new AttemptSummaryPage()
                .items(attempts.listOf(query, limit).stream()
                        .map(AttemptDtoMapper::summary)
                        .toList())
                .page(new PageInfo().hasMore(false).limit(limit)));
    }

    @Override
    public ResponseEntity<AttemptReview> getAttemptReviewInternal(UUID attemptId) {
        return ResponseEntity.ok(AttemptDtoMapper.review(attempts.reviewInternal(attemptId)));
    }

    @Override
    public ResponseEntity<AbilityList> listUserAbilitiesInternal(UUID userId) {
        return ResponseEntity.ok(new AbilityList()
                .items(abilities.abilitiesOf(userId).stream()
                        .map(AttemptDtoMapper::ability)
                        .toList()));
    }

    @Override
    public ResponseEntity<PracticeUserDataExport> exportPracticeUserData(UUID userId) {
        UserDataUseCase.UserData data = userData.export(userId);
        return ResponseEntity.ok(new PracticeUserDataExport()
                .userId(userId)
                .generatedAt(AttemptDtoMapper.time(data.generatedAt()))
                .attempts(
                        data.attempts().stream().map(AttemptDtoMapper::summary).toList())
                .abilities(
                        data.abilities().stream().map(AttemptDtoMapper::ability).toList())
                .timeEntries(data.timeEntries().stream()
                        .map(entry -> new PracticeUserDataExportTimeEntriesInner()
                                .date(entry.localDate())
                                .kind(kind(entry.contextType()))
                                .activeSeconds(entry.activeSeconds()))
                        .toList()));
    }

    private static @Nullable WeekTaskKind kind(ContextType context) {
        return switch (context) {
            case ATTEMPT -> WeekTaskKind.ITEM_SET;
            case GRAMMAR_STEP -> WeekTaskKind.GRAMMAR_UNIT;
            case MATERIAL_VIEW -> WeekTaskKind.TUTOR_MATERIAL;
            case VOCAB_REVIEW -> WeekTaskKind.VOCABULARY_REVIEW;
            case EXTERNAL_RESOURCE -> WeekTaskKind.EXTERNAL_RESOURCE;
        };
    }
}
