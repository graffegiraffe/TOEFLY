package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.call.ModelPrice;
import com.toefly.aigateway.domain.routing.ModelRef;

/** Paid prices of the models for the shadow cost (§8.4); unknown models are free. */
public interface PriceListPort {

    ModelPrice price(ModelRef model);
}
