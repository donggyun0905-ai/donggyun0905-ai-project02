package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobDaoTest {

    private final JobDao jobDao = new JobDao();

    @Test
    void findAll_returnsSeededJobs() throws Exception {
        List<JobDto> jobs = jobDao.findAll();
        assertTrue(jobs.size() >= 18, "sql/02_seed.sql should have seeded 18 IT jobs");
        assertTrue(jobs.stream().anyMatch(j -> "백엔드 개발자".equals(j.getJobName())));
    }
}
