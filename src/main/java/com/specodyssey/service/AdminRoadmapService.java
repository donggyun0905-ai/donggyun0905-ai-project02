package com.specodyssey.service;

import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 로드맵 수정(2026-10-06) — 완료 체크가 꼬인 단계를 직접 고치는 지원 도구다.
 * RoadmapService.completeStep(점수 적립 + 프로필 반영까지 하는 "정상 완료" 경로)과는 다르다 —
 * 여기서 켜고 끄는 건 완료 표시 자체만 바꾸고 점수·프로필은 손대지 않는다(중복 적립·회수를 막기 위함).
 * 점수를 다시 매기고 싶으면 사용자가 화면에서 정상적으로 완료하게 안내한다.
 */
public class AdminRoadmapService {

    private final UserDao userDao = new UserDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();

    public UserDto findUser(Long userId) throws SQLException {
        return userDao.findByIdIncludingDeleted(userId);
    }

    public RoadmapDto findPrimaryRoadmap(Long userId) throws SQLException {
        return roadmapDao.findPrimaryByUserId(userId);
    }

    public List<RoadmapStepDto> listSteps(Long roadmapId) throws SQLException {
        return roadmapStepDao.findByRoadmapId(roadmapId);
    }

    public void setCompleted(Long stepId, Long userId, boolean completed) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            int updated = roadmapStepDao.updateCompleted(conn, stepId, userId, completed,
                    completed ? LocalDateTime.now() : null);
            if (updated == 0) {
                throw new IllegalArgumentException("단계를 찾을 수 없거나 이 사용자의 로드맵이 아닙니다.");
            }
        }
    }

    // 잘못 생성돼 더는 의미 없는 단계를 지운다 — 완료한 단계는 기록이라 지우지 않는다(softDeleteIncomplete 자체 조건).
    public void deleteIncompleteStep(Long stepId, Long userId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            int updated = roadmapStepDao.softDeleteIncomplete(conn, stepId, userId);
            if (updated == 0) {
                throw new IllegalArgumentException("지울 수 없습니다 — 이미 완료된 단계이거나 이 사용자의 로드맵이 아닙니다.");
            }
        }
    }
}
