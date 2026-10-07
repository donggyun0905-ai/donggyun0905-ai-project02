package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserEducationDao;
import com.specodyssey.dto.UserEducationDto;
import com.specodyssey.util.TransactionUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Set;

/**
 * 학력(학교·졸업 상태·졸업일·학점) 저장. 관련 요구사항: FR-81 이력 · NFR-4 공개 범위
 * 면접관에게는 공유 링크에서 "학력"을 고른 경우에만 보인다(ShareViewService).
 */
public class EducationService {

    static final int SCHOOL_NAME_MAX_LENGTH = 100; // USER_EDUCATION.school_name VARCHAR(100)
    static final Set<BigDecimal> GPA_SCALES = Set.of(new BigDecimal("4.5"), new BigDecimal("4.3"), new BigDecimal("4.0"));

    private final UserEducationDao educationDao = new UserEducationDao();
    private final UserDao userDao = new UserDao();

    public UserEducationDto find(Long userId) throws SQLException {
        return educationDao.findByUserId(userId);
    }

    /**
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public void save(Long userId, UserEducationDto education) throws SQLException {
        validate(education);
        education.setUserId(userId);
        TransactionUtil.runInTransaction(conn -> {
            educationDao.upsert(conn, education);
            userDao.touchProfileUpdatedAt(conn, userId);
            return null;
        });
    }

    public void delete(Long userId) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            educationDao.softDeleteByUserId(conn, userId);
            userDao.touchProfileUpdatedAt(conn, userId);
            return null;
        });
    }

    static void validate(UserEducationDto e) {
        if (e.getSchoolName() == null || e.getSchoolName().isBlank()) {
            throw new IllegalArgumentException("학교 이름을 입력해주세요.");
        }
        e.setSchoolName(e.getSchoolName().trim());
        if (e.getSchoolName().length() > SCHOOL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("학교 이름은 " + SCHOOL_NAME_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        if (e.getGraduationStatus() == null || !UserEducationDto.STATUS_LABELS.containsKey(e.getGraduationStatus())) {
            throw new IllegalArgumentException("재학 상태를 선택해주세요.");
        }
        // 학점은 만점과 같이 넣어야 의미가 있다 — 3.8만 보고는 4.5 만점인지 4.3 만점인지 알 수 없다
        if (e.getGpa() != null) {
            if (e.getGpaMax() == null || GPA_SCALES.stream().noneMatch(s -> s.compareTo(e.getGpaMax()) == 0)) {
                throw new IllegalArgumentException("학점 만점(4.5 / 4.3 / 4.0)을 선택해주세요.");
            }
            if (e.getGpa().signum() < 0 || e.getGpa().compareTo(e.getGpaMax()) > 0) {
                throw new IllegalArgumentException("학점은 0 이상, 만점 이하로 입력해주세요.");
            }
        } else {
            e.setGpaMax(null);
        }
    }
}
