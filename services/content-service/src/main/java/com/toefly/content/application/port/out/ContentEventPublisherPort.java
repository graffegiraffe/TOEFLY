package com.toefly.content.application.port.out;

import com.toefly.content.domain.catalog.ItemSet;
import java.time.Instant;
import java.util.List;

public interface ContentEventPublisherPort {

    void itemSetPublished(ItemSet itemSet, List<ItemSetStorePort.ItemRow> items, Instant now);

    void itemSetHidden(ItemSet itemSet, Instant now);
}
