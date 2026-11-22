package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.api.InternalApi;
import com.toefly.billing.adapter.in.web.dto.BillingUserDataExport;
import com.toefly.billing.adapter.in.web.dto.EntitlementSet;
import com.toefly.billing.adapter.in.web.dto.TutorSeats;
import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.EntitlementQueryUseCase;
import com.toefly.billing.application.port.in.EntitlementView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code /internal/v1}: scope {@code internal} only (ROLE_SERVICE), docs/05 §9.3.5. */
@RestController
public class InternalBillingController implements InternalApi {

    private final EntitlementQueryUseCase entitlements;

    public InternalBillingController(EntitlementQueryUseCase entitlements) {
        this.entitlements = entitlements;
    }

    @Override
    public ResponseEntity<EntitlementSet> getEntitlementsInternal(UUID userId) {
        EntitlementView view = entitlements
                .snapshot(userId)
                .orElseThrow(() -> BillingException.notFound("Billing knows no user " + userId));
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(BillingDtoMapper.entitlements(view));
    }

    @Override
    public ResponseEntity<TutorSeats> getTutorSeatsInternal(UUID tutorId) {
        EntitlementQueryUseCase.TutorSeats seats = entitlements.tutorSeats(tutorId);
        return ResponseEntity.ok(new TutorSeats()
                .tutorId(tutorId)
                .planTierCode(seats.tier() == null ? null : BillingDtoMapper.tier(seats.tier()))
                .seatLimit(seats.seatLimit())
                .seatsUsed(seats.seatsUsed())
                .seatsFree(seats.seatLimit() - seats.seatsUsed()));
    }

    @Override
    public ResponseEntity<BillingUserDataExport> exportUserDataInternal(UUID userId) {
        EntitlementQueryUseCase.Export export = entitlements.export(userId);
        return ResponseEntity.ok(new BillingUserDataExport()
                .userId(userId)
                .subscriptions(export.subscriptions().stream()
                        .map(BillingDtoMapper::admin)
                        .toList())
                .payments(export.payments().stream()
                        .map(BillingDtoMapper::payment)
                        .toList())
                .entitlements(BillingDtoMapper.entitlements(export.entitlements())));
    }
}
