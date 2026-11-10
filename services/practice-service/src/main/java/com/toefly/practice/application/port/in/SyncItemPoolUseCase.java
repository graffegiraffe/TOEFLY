package com.toefly.practice.application.port.in;

import com.toefly.practice.application.port.out.ItemPoolPort.PoolItem;
import java.util.List;
import java.util.UUID;

/** Keeps the local projection of published sets (item-set-published, item-set-hidden). */
public interface SyncItemPoolUseCase {

    void published(UUID itemSetId, int version, String examFormat, List<PoolItem> items);

    /** New attempts on a hidden set are refused; running attempts continue (INV-ATT-05). */
    void hidden(UUID itemSetId);
}
