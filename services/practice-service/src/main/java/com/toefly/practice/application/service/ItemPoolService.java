package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.SyncItemPoolUseCase;
import com.toefly.practice.application.port.out.ItemPoolPort;
import com.toefly.practice.application.port.out.ItemPoolPort.PoolItem;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror of the published sets, the source of item identifiers and difficulties (INV-ATT-06). */
@Service
@Transactional
public class ItemPoolService implements SyncItemPoolUseCase {

    private final ItemPoolPort pool;

    public ItemPoolService(ItemPoolPort pool) {
        this.pool = pool;
    }

    @Override
    public void published(UUID itemSetId, int version, String examFormat, List<PoolItem> items) {
        pool.published(itemSetId, version, examFormat, items);
    }

    @Override
    public void hidden(UUID itemSetId) {
        pool.hidden(itemSetId);
    }
}
