package com.toefly.billing.domain.seat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Seats of one tutor subscription (INV-BIL-02, INV-BIL-06). */
public record SeatPool(UUID subscriptionId, List<Seat> seats) {

    public SeatPool {
        seats = seats.stream().sorted(Comparator.comparingInt(Seat::index)).toList();
    }

    public static SeatPool empty(UUID subscriptionId) {
        return new SeatPool(subscriptionId, List.of());
    }

    public int limit() {
        return seats.size();
    }

    public int used() {
        return (int) seats.stream().filter(seat -> !seat.free()).count();
    }

    public List<UUID> students() {
        return seats.stream().filter(seat -> !seat.free()).map(Seat::studentId).toList();
    }

    public Optional<Seat> seatOf(UUID student) {
        return seats.stream().filter(seat -> student.equals(seat.studentId())).findFirst();
    }

    /** Resizes the pool to {@code limit} seats; students of removed seats move to free kept seats. */
    public SeatPool resize(int limit, Supplier<UUID> ids) {
        requireFits(limit);
        List<Seat> kept = new ArrayList<>(
                seats.stream().filter(seat -> seat.index() <= limit).toList());
        for (int index = kept.size() + 1; index <= limit; index++) {
            kept.add(new Seat(ids.get(), subscriptionId, index, null, null));
        }
        SeatPool next = new SeatPool(subscriptionId, kept);
        for (Seat moved : seats.stream()
                .filter(seat -> seat.index() > limit && !seat.free())
                .toList()) {
            next = next.assign(moved.studentId(), moved.assignedAt());
        }
        return next;
    }

    /** Assigns a free seat; a student who already holds one keeps it. */
    public SeatPool assign(UUID student, Instant now) {
        if (seatOf(student).isPresent()) {
            return this;
        }
        Seat free = seats.stream().filter(Seat::free).findFirst().orElseThrow(() -> new NoFreeSeatException(limit()));
        return replace(free.assign(student, now));
    }

    public SeatPool release(UUID student) {
        return seatOf(student).map(seat -> replace(seat.release())).orElse(this);
    }

    public SeatPool releaseAll() {
        return new SeatPool(subscriptionId, seats.stream().map(Seat::release).toList());
    }

    /** INV-BIL-06: a tutor may move to a smaller plan only with no more students than its seats. */
    public void requireFits(int limit) {
        if (used() > limit) {
            throw new SeatsInUseException(used(), limit);
        }
    }

    private SeatPool replace(Seat changed) {
        return new SeatPool(
                subscriptionId,
                seats.stream()
                        .map(seat -> seat.id().equals(changed.id()) ? changed : seat)
                        .toList());
    }
}
