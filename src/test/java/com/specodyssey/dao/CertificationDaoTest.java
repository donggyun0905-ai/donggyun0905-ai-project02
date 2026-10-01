package com.specodyssey.dao;

import com.specodyssey.dto.CertificationDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificationDaoTest {

    private final CertificationDao certificationDao = new CertificationDao();

    @Test
    void findAll_and_findByName_returnsSeededCertifications() throws Exception {
        List<CertificationDto> certs = certificationDao.findAll();
        assertTrue(certs.size() >= 37, "sql/02_seed.sql should have seeded 37 certifications");

        CertificationDto found = certificationDao.findByName("정보처리기사");
        assertNotNull(found);
        assertEquals("한국산업인력공단", found.getIssuer());
    }

    // 2026-09-30 수정: COMMON(컴활 등 범용 자격증)이 어떤 직무를 조회해도 항상 같이 나와야 한다
    // (db-design.md·role-plan.md 명시 원칙, 누락돼 있던 걸 수정).
    @Test
    void findByJobCategory_includesCommonCertifications_sortedAfterTargetCategory() throws Exception {
        List<CertificationDto> certs = certificationDao.findByJobCategory("BACKEND");

        assertTrue(certs.stream().anyMatch(c -> "BACKEND".equals(c.getJobCategory())),
                "BACKEND 자격증이 결과에 있어야 한다");
        assertTrue(certs.stream().anyMatch(c -> "COMMON".equals(c.getJobCategory())),
                "COMMON 자격증도 결과에 같이 있어야 한다");

        int firstCommonIndex = -1;
        int lastTargetIndex = -1;
        for (int i = 0; i < certs.size(); i++) {
            String category = certs.get(i).getJobCategory();
            if ("COMMON".equals(category) && firstCommonIndex == -1) {
                firstCommonIndex = i;
            }
            if ("BACKEND".equals(category)) {
                lastTargetIndex = i;
            }
        }
        assertTrue(lastTargetIndex < firstCommonIndex,
                "대상 직무(BACKEND) 자격증이 COMMON보다 먼저 나와야 한다");
    }
}
