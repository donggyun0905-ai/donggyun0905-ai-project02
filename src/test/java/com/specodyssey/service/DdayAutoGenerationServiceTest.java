package com.specodyssey.service;

import com.specodyssey.dao.CertScheduleDao;
import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DdayAlertDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.CertScheduleDto;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DdayAutoGenerationService 통합테스트. 관련 요구사항: FR-71
 */
class DdayAutoGenerationServiceTest {

    private final UserDao userDao = new UserDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final CertScheduleDao certScheduleDao = new CertScheduleDao();
    private final DdayAlertDao ddayAlertDao = new DdayAlertDao();
    private final DdayAutoGenerationService service = new DdayAutoGenerationService();

    private Long userId;
    private Long certificationId;
    private Long pastScheduleId;
    private Long upcomingScheduleId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("dday_auto_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        certificationId = certificationDao.findAll().get(0).getId();

        try (Connection conn = DBUtil.getConnection()) {
            // 접수 마감이 이미 지난 회차 — 후보에서 제외돼야 한다.
            CertScheduleDto past = new CertScheduleDto();
            past.setCertificationId(certificationId);
            past.setRoundName("지난 회차");
            past.setApplyStart(LocalDate.now().minusDays(60));
            past.setApplyEnd(LocalDate.now().minusDays(50));
            past.setExamDate(LocalDate.now().minusDays(30));
            pastScheduleId = certScheduleDao.insert(conn, past);

            // 접수 마감이 아직 안 지난 회차 — 이게 골라져야 한다.
            CertScheduleDto upcoming = new CertScheduleDto();
            upcoming.setCertificationId(certificationId);
            upcoming.setRoundName("다음 회차");
            upcoming.setApplyStart(LocalDate.now().plusDays(5));
            upcoming.setApplyEnd(LocalDate.now().plusDays(10));
            upcoming.setExamDate(LocalDate.now().plusDays(30));
            upcomingScheduleId = certScheduleDao.insert(conn, upcoming);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "DDAY_ALERT", "user_id", userId);
            TestFixtures.hardDelete(conn, "CERT_SCHEDULE", pastScheduleId);
            TestFixtures.hardDelete(conn, "CERT_SCHEDULE", upcomingScheduleId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 접수_마감이_지나지_않은_가장_가까운_회차로_Dday를_만든다() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            service.autoCreateCertDday(conn, userId, certificationId);
        }

        List<DdayAlertDto> alerts = ddayAlertDao.findByUserId(userId);
        assertEquals(1, alerts.size());
        assertEquals(upcomingScheduleId, alerts.get(0).getCertScheduleId());
        assertEquals("CERT", alerts.get(0).getAlertType());
        assertEquals(LocalDate.now().plusDays(10), alerts.get(0).getTargetDate());
        assertTrue(alerts.get(0).getTitle().contains("다음 회차"));
    }

    @Test
    void 같은_일정으로_두번_호출해도_중복_생성되지_않는다() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            service.autoCreateCertDday(conn, userId, certificationId);
            service.autoCreateCertDday(conn, userId, certificationId);
        }

        assertEquals(1, ddayAlertDao.findByUserId(userId).size());
    }

    @Test
    void 일정이_하나도_없는_자격증이면_아무것도_안_만든다() throws Exception {
        // sql/12_seed_cert_schedule.sql이 정보처리기사·정보처리산업기사·SQLD에는 이미 예시 일정을
        // 심어뒀으니, 이 셋과 테스트가 직접 넣은 certificationId를 모두 피해야 "일정 없음" 케이스가
        // 보장된다.
        List<String> seededCertNames = List.of("정보처리기사", "정보처리산업기사", "SQLD");
        CertificationDto noScheduleCert = certificationDao.findAll().stream()
                .filter(c -> !c.getId().equals(certificationId))
                .filter(c -> !seededCertNames.contains(c.getCertName()))
                .findFirst().orElseThrow();

        try (Connection conn = DBUtil.getConnection()) {
            service.autoCreateCertDday(conn, userId, noScheduleCert.getId());
        }

        assertTrue(ddayAlertDao.findByUserId(userId).isEmpty());
    }
}
