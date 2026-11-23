package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.api.ReviewsApi;
import com.toefly.vocabulary.adapter.in.web.dto.ReviewCard;
import com.toefly.vocabulary.adapter.in.web.dto.ReviewCardList;
import com.toefly.vocabulary.adapter.in.web.dto.ReviewCardRequest;
import com.toefly.vocabulary.adapter.in.web.dto.ReviewSession;
import com.toefly.vocabulary.adapter.in.web.dto.StartReviewSessionRequest;
import com.toefly.vocabulary.application.port.in.DueView;
import com.toefly.vocabulary.application.port.in.ReviewUseCase;
import com.toefly.vocabulary.domain.fsrs.Rating;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Daily queue, review sessions and FSRS grades (US-15.03). */
@RestController
public class ReviewController implements ReviewsApi {

    private final ReviewUseCase reviews;
    private final CurrentUser user;
    private final Idempotent idempotent;

    public ReviewController(ReviewUseCase reviews, CurrentUser user, Idempotent idempotent) {
        this.reviews = reviews;
        this.user = user;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<ReviewCardList> listDueReviewCards() {
        DueView due = reviews.due(user.id());
        return ResponseEntity.ok(new ReviewCardList()
                ._localDate(due.localDate())
                .timezone(due.timezone())
                .dueCount(due.dueCount())
                .newCount(due.newCount())
                .dueTomorrow(due.dueTomorrow())
                .items(due.cards().stream().map(VocabularyDtoMapper::reviewCard).toList()));
    }

    @Override
    public ResponseEntity<ReviewSession> startReviewSession(
            String idempotencyKey, @Nullable StartReviewSessionRequest request) {
        UUID userId = user.id();
        UUID weekTaskId = request == null ? null : request.getWeekTaskId();
        return idempotent.run(
                userId,
                idempotencyKey,
                "startReviewSession",
                request,
                ReviewSession.class,
                HttpStatus.CREATED,
                () -> VocabularyDtoMapper.session(reviews.start(userId, weekTaskId)));
    }

    @Override
    public ResponseEntity<ReviewSession> getReviewSession(UUID sessionId) {
        return ResponseEntity.ok(VocabularyDtoMapper.session(reviews.session(user.id(), sessionId)));
    }

    @Override
    public ResponseEntity<ReviewCard> reviewCard(UUID cardId, String idempotencyKey, ReviewCardRequest request) {
        UUID userId = user.id();
        return idempotent.run(
                userId,
                idempotencyKey,
                "reviewCard:" + cardId,
                request,
                ReviewCard.class,
                HttpStatus.OK,
                () -> VocabularyDtoMapper.reviewCard(reviews.review(
                        userId,
                        cardId,
                        Rating.valueOf(request.getRating().name()),
                        request.getReviewSessionId(),
                        request.getSequenceNo(),
                        request.getElapsedMs() == null ? 0 : request.getElapsedMs())));
    }

    @Override
    public ResponseEntity<ReviewSession> completeReviewSession(String idempotencyKey, UUID sessionId) {
        UUID userId = user.id();
        return idempotent.run(
                userId,
                idempotencyKey,
                "completeReviewSession:" + sessionId,
                null,
                ReviewSession.class,
                HttpStatus.OK,
                () -> VocabularyDtoMapper.session(reviews.complete(userId, sessionId)));
    }
}
