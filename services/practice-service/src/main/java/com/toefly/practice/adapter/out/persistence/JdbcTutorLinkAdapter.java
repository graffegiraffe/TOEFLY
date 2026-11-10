package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.out.TutorLinkPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** tutor_student_link; filled by classroom events from phase 10. */
@Repository
public class JdbcTutorLinkAdapter implements TutorLinkPort {

    private final JdbcClient jdbc;

    public JdbcTutorLinkAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean linked(UUID tutorId, UUID studentId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM tutor_student_link WHERE tutor_id = :tutor AND student_id = :student)")
                .param("tutor", tutorId)
                .param("student", studentId)
                .query(Boolean.class)
                .single();
    }
}
