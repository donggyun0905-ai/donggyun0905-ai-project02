package com.specodyssey.service;

import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.UserSkillDto;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 격차 분석 충족·부족 목록의 "숙련도" 칸 (2026-10-12) — 요구 기술마다 프로필에 적은 숙련도를 입문·중급·고급으로 보여 준다.
 * 프로필 기술은 skill_id로 연결돼 있거나(로드맵 완료·자동 연결), 직접 입력이라 이름(raw_input)만 있을 수 있어 둘 다 본다.
 * 프로필에 없는 기술은 "—", 있는데 숙련도를 안 골랐으면 "미입력". 이름은 면접관 화면(InterviewerService)과 같다.
 */
public class GapSkillLevelService {

    static final String NOT_OWNED = "—";
    static final String UNSET = "미입력";
    private static final Map<String, String> LABELS = Map.of(
            "BEGINNER", "입문", "INTERMEDIATE", "중급", "ADVANCED", "고급");

    private final UserSkillDao userSkillDao;

    public GapSkillLevelService() {
        this(new UserSkillDao());
    }

    GapSkillLevelService(UserSkillDao userSkillDao) {
        this.userSkillDao = userSkillDao;
    }

    public List<UserSkillDto> ownedSkills(Long userId) throws SQLException {
        return userSkillDao.findByUserId(userId);
    }

    /** 요구 기술 하나의 숙련도 문구 — owned는 ownedSkills()로 한 번 읽어 둔 목록 */
    public static String levelLabel(List<UserSkillDto> owned, Long skillId, String skillName) {
        String nameKey = normalize(skillName);
        UserSkillDto match = null;
        for (UserSkillDto skill : owned) {
            boolean sameId = skillId != null && skillId.equals(skill.getSkillId());
            boolean sameName = !nameKey.isEmpty() && nameKey.equals(normalize(skill.getRawInput()));
            if (sameId || sameName) {
                // 같은 기술이 두 줄이면 숙련도가 높은 쪽
                if (match == null || rank(skill.getProficiency()) > rank(match.getProficiency())) {
                    match = skill;
                }
            }
        }
        if (match == null) {
            return NOT_OWNED;
        }
        return LABELS.getOrDefault(match.getProficiency() == null ? "" : match.getProficiency(), UNSET);
    }

    private static int rank(String proficiency) {
        if ("ADVANCED".equals(proficiency)) {
            return 3;
        } else if ("INTERMEDIATE".equals(proficiency)) {
            return 2;
        } else if ("BEGINNER".equals(proficiency)) {
            return 1;
        }
        return 0;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
