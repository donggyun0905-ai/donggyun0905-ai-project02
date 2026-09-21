package com.specodyssey.dao;

import com.specodyssey.dto.CertScheduleDto;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertScheduleDaoTest {

    private final CertScheduleDao dao = new CertScheduleDao();
    private final CertificationDao certificationDao = new CertificationDao();

    @Test
    void insert_findByCertificationId() throws Exception {
        CertificationDto cert = certificationDao.findAll().get(0);

        CertScheduleDto schedule = new CertScheduleDto();
        schedule.setCertificationId(cert.getId());
        schedule.setRoundName("2026년 1회");
        schedule.setApplyStart(LocalDate.of(2026, 1, 1));
        schedule.setApplyEnd(LocalDate.of(2026, 1, 10));
        schedule.setExamDate(LocalDate.of(2026, 2, 1));

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, schedule);
        }
        try {
            assertNotNull(id);
            List<CertScheduleDto> schedules = dao.findByCertificationId(cert.getId());
            assertTrue(schedules.stream().anyMatch(s -> s.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "CERT_SCHEDULE", id);
            }
        }
    }
}
