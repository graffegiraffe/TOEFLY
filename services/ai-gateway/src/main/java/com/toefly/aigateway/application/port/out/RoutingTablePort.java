package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.RoutingTable;

/** The current table of routes (ConfigMap {@code ai-gateway-routes}, re-read when it changes). */
public interface RoutingTablePort {

    RoutingTable current();
}
