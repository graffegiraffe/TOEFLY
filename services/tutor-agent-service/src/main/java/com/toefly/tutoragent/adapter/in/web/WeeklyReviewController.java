package com.toefly.tutoragent.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.tutoragent.adapter.in.web.api.WeeklyReviewsApi;
import com.toefly.tutoragent.adapter.in.web.dto.PageInfo;
import com.toefly.tutoragent.adapter.in.web.dto.TutorNoteRequest;
import com.toefly.tutoragent.adapter.in.web.dto.WeeklyReview;
import com.toefly.tutoragent.adapter.in.web.dto.WeeklyReviewPage;
import com.toefly.tutoragent.application.port.in.WeeklyReviewUseCase;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Weekly reviews of the learner (US-18.01, US-18.03); tutor notes come with phase 10. */
@RestController
@PreAuthorize("hasRole('STUDENT')")
class WeeklyReviewController implements WeeklyReviewsApi {

    static final String FINGERPRINT = "weekly-reviews";

    private final WeeklyReviewUseCase reviews;
    private final CurrentUser user;
    private final Taxonomy taxonomy;

    WeeklyReviewController(WeeklyReviewUseCase reviews, CurrentUser user, Taxonomy taxonomy) {
        this.reviews = reviews;
        this.user = user;
        this.taxonomy = taxonomy;
    }

    @Override
    public ResponseEntity<WeeklyReview> getWeeklyReview(UUID reviewId) {
        UUID me = user.id();
        return reviews.get(me, reviewId)
                .map(r -> ResponseEntity.ok(TutorAgentDtoMapper.review(r, taxonomy)))
                .orElseThrow(ApiException::notFound);
    }

    @Override
    public ResponseEntity<WeeklyReviewPage> listWeeklyReviews(
            Integer limit, @Nullable String cursor, @Nullable UUID weekId) {
        UUID me = user.id();
        int size = CursorCodec.limit(limit);
        Integer before = cursor == null
                ? null
                : Integer.valueOf(CursorCodec.decode(cursor, FINGERPRINT).getFirst());
        List<com.toefly.tutoragent.domain.review.WeeklyReview> page = reviews.list(me, weekId, before, size + 1);
        boolean more = page.size() > size;
        List<com.toefly.tutoragent.domain.review.WeeklyReview> items = more ? page.subList(0, size) : page;
        String next = more
                ? CursorCodec.encode(
                        FINGERPRINT, List.of(Integer.toString(items.getLast().weekNo())))
                : null;
        return ResponseEntity.ok(new WeeklyReviewPage()
                .items(items.stream()
                        .map(r -> TutorAgentDtoMapper.review(r, taxonomy))
                        .toList())
                .page(new PageInfo().hasMore(more).limit(size).nextCursor(next)));
    }

    @Override
    public ResponseEntity<WeeklyReview> putWeeklyReviewTutorNote(
            String ifMatch, UUID reviewId, TutorNoteRequest tutorNoteRequest) {
        throw ApiException.notImplemented("10");
    }
}
