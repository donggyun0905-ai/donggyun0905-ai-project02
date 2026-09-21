package com.specodyssey.dao;

import com.specodyssey.dto.JobBenchmarkSpecDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobBenchmarkSpecDaoTest {

    private final JobBenchmarkSpecDao dao = new JobBenchmarkSpecDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId() throws Exception {
        JobDto job = jobDao.findAll().get(0);

        JobBenchmarkSpecDto spec = new JobBenchmarkSpecDto();
        spec.setJobId(job.getId());
        spec.setTier("EXPERT");
        spec.setSpecType("CERT");
        spec.setContent("정보처리기사");
        spec.setEstimated(true);
        spec.setGeneratedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, spec);
        }
        try {
            assertNotNull(id);
            List<JobBenchmarkSpecDto> specs = dao.findByJobId(job.getId());
            assertTrue(specs.stream().anyMatch(s -> s.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "JOB_BENCHMARK_SPEC", id);
            }
        }
    }
}
