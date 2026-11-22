package com.toefly.billing.adapter.out.persistence;

import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.domain.seat.Seat;
import com.toefly.billing.domain.seat.SeatPool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Writers change seats under the lock of their subscription row or saga (INV-BIL-02); reads take no lock. */
@Repository
public class JdbcSeatRepositoryAdapter implements SeatRepository {

    private final JdbcClient jdbc;

    public JdbcSeatRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SeatPool load(UUID subscriptionId) {
        List<Seat> seats = jdbc.sql("""
                        SELECT id, subscription_id, index_no, student_id, assigned_at
                        FROM seat WHERE subscription_id = :id ORDER BY index_no
                        """)
                .param("id", subscriptionId)
                .query((rs, row) -> new Seat(
                        rs.getObject("id", UUID.class),
                        rs.getObject("subscription_id", UUID.class),
                        rs.getInt("index_no"),
                        Sql.uuid(rs, "student_id"),
                        Sql.instant(rs, "assigned_at")))
                .list();
        return new SeatPool(subscriptionId, seats);
    }

    @Override
    public void save(SeatPool pool) {
        List<UUID> ids = pool.seats().stream().map(Seat::id).toList();
        jdbc.sql("DELETE FROM seat WHERE subscription_id = :sub AND NOT (id = ANY(:ids))")
                .param("sub", pool.subscriptionId())
                .param("ids", ids.toArray(new UUID[0]))
                .update();
        // Free the students first so the unique index on student_id never sees a seat taken twice.
        jdbc.sql("UPDATE seat SET student_id = NULL, assigned_at = NULL WHERE subscription_id = :sub")
                .param("sub", pool.subscriptionId())
                .update();
        for (Seat seat : pool.seats()) {
            jdbc.sql("""
                            INSERT INTO seat (id, subscription_id, index_no, student_id, assigned_at)
                            VALUES (:id, :sub, :index, :student, :assigned)
                            ON CONFLICT (id) DO UPDATE SET index_no = EXCLUDED.index_no,
                                student_id = EXCLUDED.student_id, assigned_at = EXCLUDED.assigned_at
                            """)
                    .param("id", seat.id())
                    .param("sub", pool.subscriptionId())
                    .param("index", seat.index())
                    .param("student", seat.studentId())
                    .param("assigned", Sql.ts(seat.assignedAt()))
                    .update();
        }
    }

    @Override
    public Optional<Holding> findByStudent(UUID studentId) {
        return jdbc.sql("""
                        SELECT s.subscription_id, sub.user_id
                        FROM seat s JOIN subscription sub ON sub.id = s.subscription_id
                        WHERE s.student_id = :student
                        """)
                .param("student", studentId)
                .query((rs, row) ->
                        new Holding(rs.getObject("subscription_id", UUID.class), rs.getObject("user_id", UUID.class)))
                .optional();
    }
}
