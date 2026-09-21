package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.dto.TrendTechJobDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendTechJobDaoTest {

    private final TrendTechJobDao dao = new TrendTechJobDao();
    private final TrendTechDao trendTechDao = new TrendTechDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId() throws Exception {
        JobDto job = jobDao.findAll().get(0);

        TrendTechDto trendTech = new TrendTechDto();
        trendTech.setTechName("Docker_" + System.nanoTime());
        trendTech.setSummary("컨테이너 채용 언급 증가");
        trendTech.setPublishedAt(LocalDateTime.now());

        Long trendTechId;
        try (Connection conn = DBUtil.getConnection()) {
            trendTechId = trendTechDao.insert(conn, trendTech);
        }

        try {
            TrendTechJobDto link = new TrendTechJobDto();
            link.setTrendTechId(trendTechId);
            link.setJobId(job.getId());
            link.setRelevanceScore(new BigDecimal("0.9000"));

            Long linkId;
            try (Connection conn = DBUtil.getConnection()) {
                linkId = dao.insert(conn, link);
            }
            assertNotNull(linkId);

            List<TrendTechJobDto> links = dao.findByJobId(job.getId());
            assertTrue(links.stream().anyMatch(l -> l.getId().equals(linkId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "TREND_TECH_JOB", "trend_tech_id", trendTechId);
                TestFixtures.hardDelete(conn, "TREND_TECH", trendTechId);
            }
        }
    }
}
