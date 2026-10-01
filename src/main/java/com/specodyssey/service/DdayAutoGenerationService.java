package com.specodyssey.service;

import com.specodyssey.dao.CertScheduleDao;
import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DdayAlertDao;
import com.specodyssey.dto.CertScheduleDto;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DdayAlertDto;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * CERT_SCHEDULE → DDAY_ALERT 자동 생성. 관련 요구사항: FR-71
 *
 * 로드맵이 사용자에게 어떤 자격증을 추천하는 바로 그 시점에(RoadmapService.generate()), 그
 * 자격증의 접수 마감이 아직 안 지난 가장 가까운 시험 회차가 있으면 D-day를 자동으로 만들어준다.
 * "이미 딴 자격증"(USER_SPECS)이 아니라 "앞으로 봐야 할 시험"을 알려주는 것이라 로드맵 CERT 단계
 * 추천 시점에 거는 게 자연스럽다 — 프로필에서 자격증을 직접 추가하는 흐름과는 별개다.
 */
public class DdayAutoGenerationService {

    private static final String ALERT_TYPE_CERT = "CERT";

    private final CertScheduleDao certScheduleDao = new CertScheduleDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final DdayAlertDao ddayAlertDao = new DdayAlertDao();

    /**
     * 이미 같은 일정(cert_schedule_id)으로 만들어둔 D-day가 있으면 아무 것도 안 한다
     * (DDAY_ALERT의 UNIQUE(user_id, cert_schedule_id) 제약과 같은 의도 — 여기서 먼저 걸러서
     * 매번 제약 위반 예외에 의존하지 않는다). CERT_SCHEDULE에 등록된 일정이 아직 없으면(수집
     * 전이거나 상시 시험이라 회차가 없는 자격증) 조용히 넘어간다 — 로드맵 생성 자체를 막지 않는다.
     */
    public void autoCreateCertDday(Connection conn, Long userId, Long certificationId) throws SQLException {
        CertScheduleDto nextRound = findNextUpcomingRound(certificationId);
        if (nextRound == null) {
            return;
        }
        boolean alreadyExists = ddayAlertDao.findByUserId(userId).stream()
                .anyMatch(a -> nextRound.getId().equals(a.getCertScheduleId()));
        if (alreadyExists) {
            return;
        }

        CertificationDto cert = certificationDao.findById(conn, certificationId);
        String certName = cert == null ? "자격증" : cert.getCertName();

        DdayAlertDto alert = new DdayAlertDto();
        alert.setUserId(userId);
        alert.setCertScheduleId(nextRound.getId());
        alert.setTitle(certName + " " + nextRound.getRoundName() + " 접수 마감");
        alert.setTargetDate(nextRound.getApplyEnd());
        alert.setAlertType(ALERT_TYPE_CERT);
        alert.setNotified(false);
        ddayAlertDao.insert(conn, alert);
    }

    // 접수 마감이 지나지 않은 회차 중 시험일이 가장 가까운 것.
    private CertScheduleDto findNextUpcomingRound(Long certificationId) throws SQLException {
        LocalDate today = LocalDate.now();
        List<CertScheduleDto> schedules = certScheduleDao.findByCertificationId(certificationId);
        return schedules.stream()
                .filter(s -> s.getApplyEnd() != null && !s.getApplyEnd().isBefore(today))
                .min(Comparator.comparing(CertScheduleDto::getExamDate))
                .orElse(null);
    }
}
