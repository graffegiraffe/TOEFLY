package com.toefly.billing.adapter.in.web;

import com.toefly.billing.adapter.in.web.dto.AdminSubscription;
import com.toefly.billing.adapter.in.web.dto.CheckoutSession;
import com.toefly.billing.adapter.in.web.dto.EntitlementGrant;
import com.toefly.billing.adapter.in.web.dto.EntitlementSet;
import com.toefly.billing.adapter.in.web.dto.FreeAiChecks;
import com.toefly.billing.adapter.in.web.dto.Payment;
import com.toefly.billing.adapter.in.web.dto.PaymentProviderOption;
import com.toefly.billing.adapter.in.web.dto.PlanTier;
import com.toefly.billing.adapter.in.web.dto.PlanTierList;
import com.toefly.billing.adapter.in.web.dto.ProviderMode;
import com.toefly.billing.adapter.in.web.dto.SagaInstance;
import com.toefly.billing.adapter.in.web.dto.SagaStep;
import com.toefly.billing.adapter.in.web.dto.Seat;
import com.toefly.billing.adapter.in.web.dto.SeatSummary;
import com.toefly.billing.adapter.in.web.dto.Subscription;
import com.toefly.billing.application.port.in.EntitlementView;
import com.toefly.billing.application.port.in.PlanCatalogUseCase;
import com.toefly.billing.application.port.in.SubscriptionView;
import com.toefly.billing.domain.entitlement.Entitlement;
import com.toefly.billing.domain.payment.PaymentStatus;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Domain → generated DTOs of billing-service.yaml. */
final class BillingDtoMapper {

    private BillingDtoMapper() {}

    static PlanTierList catalog(PlanCatalogUseCase.Catalog catalog) {
        return new PlanTierList()
                .items(catalog.tiers().stream().map(BillingDtoMapper::tier).toList())
                .providers(catalog.providers().stream()
                        .map(option -> new PaymentProviderOption()
                                .code(provider(option.code()))
                                .mode(option.testMode() ? ProviderMode.TEST : ProviderMode.LIVE))
                        .toList());
    }

    static PlanTier tier(com.toefly.billing.domain.plan.PlanTier tier) {
        return new PlanTier()
                .code(tier(tier.code()))
                .audience(PlanTier.AudienceEnum.fromValue(tier.code().audience().name()))
                .priceMinor(tier.priceMinor())
                .currency(tier.currency())
                .interval(PlanTier.IntervalEnum.MONTH)
                .seatLimit(tier.seatLimit())
                .storageBytes(tier.storageBytes())
                .trialDays(tier.trialDays())
                .freeAiCheckLimit(tier.freeAiCheckLimit())
                .entitlements(tier.entitlements().stream()
                        .sorted()
                        .map(BillingDtoMapper::entitlement)
                        .toList());
    }

    static CheckoutSession session(com.toefly.billing.domain.checkout.CheckoutSession s) {
        return new CheckoutSession()
                .id(s.id())
                .provider(provider(s.provider()))
                .planTierCode(tier(s.tier()))
                .status(CheckoutSession.StatusEnum.fromValue(s.status().name()))
                .redirectUrl(s.redirectUrl() == null ? "" : s.redirectUrl())
                .expiresAt(time(s.expiresAt()))
                .testMode(s.testMode())
                .failureReason(s.failureReason())
                .failureMessage(s.failureMessage());
    }

    static Subscription subscription(SubscriptionView view) {
        var own = view.subscription();
        Subscription dto = new Subscription()
                .planTierCode(tier(view.tier()))
                .source(
                        view.source() == null
                                ? Subscription.SourceEnum.NONE
                                : Subscription.SourceEnum.fromValue(
                                        view.source().name()))
                .tutorId(view.tutorId())
                .sagaStatus(
                        view.sagaStatus() == null
                                ? null
                                : com.toefly.billing.adapter.in.web.dto.SagaStatus.fromValue(
                                        view.sagaStatus().name()))
                .cancelAtPeriodEnd(false)
                .testMode(false);
        if (own != null) {
            dto.id(own.id())
                    .status(status(own.status()))
                    .provider(own.provider() == null ? null : provider(own.provider()))
                    .currentPeriodStart(time(own.periodStart()))
                    .currentPeriodEnd(time(own.periodEnd()))
                    .trialEndsAt(time(own.trialEndsAt()))
                    .cancelAtPeriodEnd(own.cancelAtPeriodEnd())
                    .canceledAt(time(own.canceledAt()))
                    .graceUntil(time(own.graceUntil()))
                    .pendingPlanTierCode(own.pendingTier() == null ? null : tier(own.pendingTier()))
                    .testMode(own.testMode());
        }
        if (view.seats() != null) {
            dto.seats(seats(view.seats()));
        }
        return dto;
    }

    static SeatSummary seats(SeatPool pool) {
        return new SeatSummary()
                .limit(pool.limit())
                .used(pool.used())
                .seats(pool.seats().stream()
                        .map(seat -> new Seat()
                                .index(seat.index())
                                .studentId(seat.studentId())
                                .assignedAt(time(seat.assignedAt())))
                        .toList());
    }

    static Payment payment(com.toefly.billing.domain.payment.Payment p) {
        return new Payment()
                .id(p.id())
                .provider(provider(p.provider()))
                .planTierCode(tier(p.tier()))
                .amountMinor(p.amountMinor())
                .currency(p.currency())
                .status(Payment.StatusEnum.fromValue(p.status().name()))
                .paidAt(time(p.paidAt()))
                .failureReason(p.status() == PaymentStatus.FAILED ? p.failureReason() : null)
                .receiptUrl(p.receiptUrl())
                .testMode(p.testMode())
                .createdAt(time(p.createdAt()));
    }

    static EntitlementSet entitlements(EntitlementView view) {
        var set = view.set();
        return new EntitlementSet()
                .userId(set.userId())
                .entitlementsVersion(view.version())
                .planTierCode(tier(set.planTierCode()))
                .entitlements(set.entitlements().stream()
                        .sorted()
                        .map(BillingDtoMapper::entitlement)
                        .toList())
                .grants(set.grants().stream()
                        .map(grant -> new EntitlementGrant()
                                .entitlement(entitlement(grant.entitlement()))
                                .source(com.toefly.billing.adapter.in.web.dto.EntitlementSource.fromValue(
                                        grant.source().name()))
                                .limit(grant.limit())
                                .used(grant.used())
                                .validUntil(time(grant.validUntil())))
                        .toList())
                .trialEndsAt(time(set.trialEndsAt()))
                .tutorId(set.tutorId())
                .freeAiChecks(new FreeAiChecks()
                        .limit(set.freeAiChecks().limit())
                        .used(set.freeAiChecks().used())
                        .remaining(set.freeAiChecks().remaining()))
                .changedAt(time(view.changedAt()));
    }

    static AdminSubscription admin(com.toefly.billing.domain.subscription.Subscription s) {
        return new AdminSubscription()
                .id(s.id())
                .userId(s.userId())
                .planTierCode(tier(s.tier()))
                .status(status(s.status()))
                .provider(s.provider() == null ? null : provider(s.provider()))
                .currentPeriodEnd(time(s.periodEnd()))
                .trialEndsAt(time(s.trialEndsAt()))
                .cancelAtPeriodEnd(s.cancelAtPeriodEnd())
                .createdAt(time(s.createdAt()));
    }

    static SagaInstance saga(PaymentSaga saga) {
        List<SagaStep> steps = saga.steps().stream()
                .map(step -> new SagaStep()
                        .stepNo(step.step().no())
                        .name(step.step().name())
                        .status(SagaStep.StatusEnum.fromValue(step.status().name()))
                        .attempts(step.attempts())
                        .nextAttemptAt(time(step.nextAttemptAt()))
                        .lastError(step.lastError()))
                .toList();
        return new SagaInstance()
                .id(saga.id())
                .sagaType(PaymentSaga.TYPE)
                .status(com.toefly.billing.adapter.in.web.dto.SagaStatus.fromValue(
                        saga.status().name()))
                .currentStep(saga.currentStep())
                .userId(saga.userId())
                .planTierCode(tier(saga.tier()))
                .provider(provider(saga.provider()))
                .updatedAt(time(saga.updatedAt()))
                .steps(steps);
    }

    static com.toefly.billing.adapter.in.web.dto.PlanTierCode tier(PlanTierCode code) {
        return com.toefly.billing.adapter.in.web.dto.PlanTierCode.fromValue(code.name());
    }

    static com.toefly.billing.adapter.in.web.dto.PaymentProviderCode provider(PaymentProviderCode code) {
        return com.toefly.billing.adapter.in.web.dto.PaymentProviderCode.fromValue(code.name());
    }

    static com.toefly.billing.adapter.in.web.dto.SubscriptionStatus status(SubscriptionStatus status) {
        return com.toefly.billing.adapter.in.web.dto.SubscriptionStatus.fromValue(status.name());
    }

    static com.toefly.billing.adapter.in.web.dto.Entitlement entitlement(Entitlement entitlement) {
        return com.toefly.billing.adapter.in.web.dto.Entitlement.fromValue(entitlement.name());
    }

    static @Nullable OffsetDateTime time(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
