package com.toefly.assessment.adapter.out.persistence;

import com.toefly.assessment.application.port.out.TutorLinkRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code tutor_student_link}. */
@Repository
public class JdbcTutorLinkAdapter implements TutorLinkRepository {

    private final JdbcClient jdbc;

    public JdbcTutorLinkAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean linked(UUID tutorId, UUID studentId) {
        return jdbc.sql("SELECT count(*) FROM tutor_student_link WHERE tutor_id = :t AND student_id = :s")
                        .param("t", tutorId)
                        .param("s", studentId)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    @Override
    public void link(UUID tutorId, UUID studentId) {
        jdbc.sql("INSERT INTO tutor_student_link (tutor_id, student_id) VALUES (:t, :s) ON CONFLICT DO NOTHING")
                .param("t", tutorId)
                .param("s", studentId)
                .update();
    }

    @Override
    public void unlink(UUID tutorId, UUID studentId) {
        jdbc.sql("DELETE FROM tutor_student_link WHERE tutor_id = :t AND student_id = :s")
                .param("t", tutorId)
                .param("s", studentId)
                .update();
    }
}
