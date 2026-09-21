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
}
