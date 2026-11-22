package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.plan.PlanTier;
import java.util.List;

public interface PlanTierRepository {

    /** All tiers in the order of the price list. */
    List<PlanTier> findAll();
}
