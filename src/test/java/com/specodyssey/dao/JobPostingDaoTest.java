package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobPostingDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobPostingDaoTest {

    private final JobPostingDao dao = new JobPostingDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId_existsBySourceUrl() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        String sourceUrl = "https://work24.example.com/posting/" + System.nanoTime();

        JobPostingDto posting = new JobPostingDto();
        posting.setJobId(job.getId());
        posting.setTitle("테스트 채용공고");
        posting.setSummary("서비스 백엔드 API를 개발합니다.");
        posting.setQualifications("Java 3년 이상");
        posting.setPreferred("Spring Boot 경험");
        posting.setEducationLevel("대졸이상");
        posting.setSalary("회사내규에 따름");
        posting.setSourceUrl(sourceUrl);
        posting.setCollectedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, posting);
        }
        try {
            assertNotNull(id);
            List<JobPostingDto> postings = dao.findByJobId(job.getId());
            assertTrue(postings.stream().anyMatch(p -> p.getId().equals(id)));

            try (Connection conn = DBUtil.getConnection()) {
                assertTrue(dao.existsBySourceUrl(conn, sourceUrl));
                assertFalse(dao.existsBySourceUrl(conn, sourceUrl + "_없음"));
            }
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "JOB_POSTING", id);
            }
        }
    }
}
