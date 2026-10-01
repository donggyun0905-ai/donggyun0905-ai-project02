package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobPostingDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobPostingDaoTest {

    private final JobPostingDao dao = new JobPostingDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId_existsBySourceUrl() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        String sourceUrl = "https://wanted.example.com/posting/" + System.nanoTime();

        JobPostingDto posting = new JobPostingDto();
        posting.setJobId(job.getId());
        posting.setSource("원티드");
        posting.setSourceUrl(sourceUrl);
        posting.setTitle("백엔드 엔지니어 (3년 이상)");
        posting.setCompanyName("테스트컴퍼니");
        posting.setSummary("서비스 백엔드 API를 개발합니다.");
        posting.setTechStack("Java,Spring Boot,MySQL,AWS");
        posting.setQualifications("Java 3년 이상");
        posting.setPreferred("Spring Boot 경험");
        posting.setCareerLevel("경력");
        posting.setEducationLevel("학력무관");
        posting.setSalary("회사내규에 따름");
        posting.setRegion("서울 강남구");
        posting.setDeadline("상시");
        posting.setPostedAt(LocalDate.now());
        posting.setCollectedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, posting);
        }
        try {
            assertNotNull(id);
            List<JobPostingDto> postings = dao.findByJobId(job.getId());
            JobPostingDto found = postings.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
            assertEquals("원티드", found.getSource());
            assertEquals("테스트컴퍼니", found.getCompanyName());
            assertEquals("Java,Spring Boot,MySQL,AWS", found.getTechStack());
            assertEquals("경력", found.getCareerLevel());
            assertEquals("서울 강남구", found.getRegion());
            assertEquals("상시", found.getDeadline());
            assertEquals(LocalDate.now(), found.getPostedAt());

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
