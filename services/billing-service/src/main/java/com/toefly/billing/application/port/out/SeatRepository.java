package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.seat.SeatPool;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository {

    SeatPool load(UUID subscriptionId);

    void save(SeatPool pool);

    /** The seat a student holds, with its tutor subscription. */
    Optional<Holding> findByStudent(UUID studentId);

    record Holding(UUID subscriptionId, UUID tutorId) {}
}
