package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.adapter.in.web.api.SubmissionsApi;
import com.toefly.assessment.adapter.in.web.dto.CreateSubmissionRequest;
import com.toefly.assessment.adapter.in.web.dto.PageInfo;
import com.toefly.assessment.adapter.in.web.dto.SaveDraftRequest;
import com.toefly.assessment.adapter.in.web.dto.Submission;
import com.toefly.assessment.adapter.in.web.dto.SubmissionStatus;
import com.toefly.assessment.adapter.in.web.dto.SubmissionSummaryPage;
import com.toefly.assessment.adapter.in.web.dto.SubmissionVersion;
import com.toefly.assessment.adapter.in.web.dto.SubmissionVersionList;
import com.toefly.assessment.adapter.in.web.dto.TaskType;
import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.CreateSubmissionCommand;
import com.toefly.assessment.application.port.in.SubmissionCommandUseCase;
import com.toefly.assessment.application.port.in.SubmissionQueryUseCase;
import com.toefly.assessment.application.port.in.SubmissionView;
import com.toefly.assessment.domain.submission.AttemptMode;
import com.toefly.assessment.domain.submission.SubmissionRuleException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The Writing loop over REST (docs/05-api-contracts.md §6.5). */
@RestController
public class SubmissionController implements SubmissionsApi {

    private final SubmissionCommandUseCase commands;
    private final SubmissionQueryUseCase queries;
    private final CurrentCaller caller;
    private final IdempotentExecution idempotent;
    private final Clock clock;

    public SubmissionController(
            SubmissionCommandUseCase commands,
            SubmissionQueryUseCase queries,
            CurrentCaller caller,
            IdempotentExecution idempotent,
            Clock clock) {
        this.commands = commands;
        this.queries = queries;
        this.caller = caller;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<Submission> createSubmission(UUID idempotencyKey, CreateSubmissionRequest request) {
        Caller who = caller.get();
        AttemptMode mode = request.getMode() == null
                ? AttemptMode.PRACTICE
                : AttemptMode.valueOf(request.getMode().getValue());
        return idempotent.run(who.userId(), idempotencyKey, "createSubmission", request, Submission.class, () -> {
            var created = commands.create(
                    who,
                    new CreateSubmissionCommand(
                            request.getItemSetId(), request.getWeekTaskId(), request.getExamSessionId(), mode));
            Submission body = view(who, created.id());
            return new IdempotentExecution.Outcome<>(
                    HttpStatus.CREATED, body, "/api/v1/submissions/" + created.id(), ETags.of(body.getVersion()));
        });
    }

    @Override
    public ResponseEntity<SubmissionSummaryPage> listSubmissions(
            @Nullable UUID studentId,
            @Nullable TaskType taskType,
            @Nullable SubmissionStatus status,
            @Nullable UUID weekTaskId,
            @Nullable String cursor,
            Integer limit) {
        int offset = Cursors.offset(cursor);
        var filter = new com.toefly.assessment.application.port.in.SubmissionFilter(
                taskType == null || !com.toefly.assessment.domain.submission.TaskType.supports(taskType.getValue())
                        ? null
                        : com.toefly.assessment.domain.submission.TaskType.valueOf(taskType.getValue()),
                status == null
                        ? null
                        : com.toefly.assessment.domain.submission.SubmissionStatus.valueOf(status.getValue()),
                weekTaskId);
        if (taskType != null && filter.taskType() == null) {
            return ResponseEntity.ok(new SubmissionSummaryPage()
                    .items(List.of())
                    .page(new PageInfo().hasMore(false).limit(limit)));
        }
        var items = queries.list(caller.get(), studentId, filter, offset, limit + 1);
        boolean more = items.size() > limit;
        return ResponseEntity.ok(new SubmissionSummaryPage()
                .items(items.stream()
                        .limit(limit)
                        .map(AssessmentDtoMapper::summary)
                        .toList())
                .page(new PageInfo()
                        .hasMore(more)
                        .limit(limit)
                        .nextCursor(more ? Cursors.next(offset + limit) : null)));
    }

    @Override
    public ResponseEntity<Submission> getSubmission(UUID submissionId) {
        Submission body = view(caller.get(), submissionId);
        return ResponseEntity.ok().eTag(ETags.of(body.getVersion())).body(body);
    }

    @Override
    public ResponseEntity<SubmissionVersion> saveDraft(String ifMatch, UUID submissionId, SaveDraftRequest request) {
        Caller who = caller.get();
        var saved = commands.saveDraft(
                who, submissionId, request.getText(), request.getElapsedSeconds(), ETags.version(ifMatch));
        SubmissionView view = queries.get(who, saved.id());
        return ResponseEntity.ok()
                .eTag(ETags.of(saved.version()))
                .body(AssessmentDtoMapper.version(saved.current(), view.evaluations(), null));
    }

    @Override
    public ResponseEntity<Submission> submitSubmission(UUID idempotencyKey, UUID submissionId) {
        Caller who = caller.get();
        return idempotent.run(
                who.userId(), idempotencyKey, "submitSubmission:" + submissionId, null, Submission.class, () -> {
                    commands.submit(who, submissionId);
                    Submission body = view(who, submissionId);
                    return new IdempotentExecution.Outcome<>(
                            HttpStatus.ACCEPTED, body, null, ETags.of(body.getVersion()));
                });
    }

    @Override
    public ResponseEntity<SubmissionVersion> createVersion(UUID idempotencyKey, UUID submissionId) {
        Caller who = caller.get();
        return idempotent.run(
                who.userId(), idempotencyKey, "createVersion:" + submissionId, null, SubmissionVersion.class, () -> {
                    var revised = commands.startRevision(who, submissionId);
                    SubmissionView view = queries.get(who, submissionId);
                    return new IdempotentExecution.Outcome<>(
                            HttpStatus.CREATED,
                            AssessmentDtoMapper.version(revised.current(), view.evaluations(), null),
                            null,
                            ETags.of(revised.version()));
                });
    }

    @Override
    public ResponseEntity<SubmissionVersionList> listVersions(UUID submissionId) {
        return ResponseEntity.ok(new SubmissionVersionList()
                .items(view(caller.get(), submissionId).getVersions()));
    }

    @Override
    public ResponseEntity<SubmissionVersion> getVersion(UUID submissionId, Integer versionNo) {
        return view(caller.get(), submissionId).getVersions().stream()
                .filter(v -> v.getVersionNo().equals(versionNo))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SubmissionRuleException("not-found", "No version " + versionNo + "."));
    }

    private Submission view(Caller who, UUID submissionId) {
        return AssessmentDtoMapper.submission(queries.get(who, submissionId), clock.instant());
    }
}
