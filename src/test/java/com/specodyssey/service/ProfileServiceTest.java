package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ProfileService.resolveJobQuery 통합테스트. 실제 spec_odyssey DB로 검증한다.
 * sql/04_seed_skills.sql로 JOB/JOB_ALIAS가 이미 시드되어 있다는 전제
 * ("데이터 엔지니어" 직무에 "빅데이터 엔지니어" 등 별칭이 등록돼 있음).
 */
class ProfileServiceTest {

    private final ProfileService profileService = new ProfileService();

    @Test
    void 정식_명칭을_그대로_입력하면_정확_매칭된다() throws Exception {
        ProfileService.JobMatch match = profileService.resolveJobQuery("데이터 엔지니어");

        assertTrue(match.isExact());
        assertEquals("데이터 엔지니어", match.getJob().getJobName());
    }

    @Test
    void 등록된_별칭으로_입력해도_정확_매칭된다() throws Exception {
        ProfileService.JobMatch match = profileService.resolveJobQuery("빅데이터 엔지니어");

        assertTrue(match.isExact());
        assertEquals("데이터 엔지니어", match.getJob().getJobName());
    }

    @Test
    void 오타가_있어도_편집거리로_가장_가까운_직무를_찾고_확인용_별칭목록도_같이_준다() throws Exception {
        ProfileService.JobMatch match = profileService.resolveJobQuery("데이타 엔지니어"); // '터'->'타' 오타

        assertFalse(match.isExact());
        assertEquals("데이터 엔지니어", match.getJob().getJobName());
        // 확인 화면에서 "이 직무가 뭘 하는 직무인지" 보여줄 근거 — sql/04_seed_skills.sql에 등록된 별칭들.
        assertTrue(match.getAliasNames().contains("빅데이터 엔지니어"));
    }

    @Test
    void 너무_동떨어진_입력은_아무것도_찾지_못한다() throws Exception {
        ProfileService.JobMatch match = profileService.resolveJobQuery("전혀상관없는이상한직무명입니다12345");

        assertNull(match);
    }

    @Test
    void 빈_입력은_null을_반환한다() throws Exception {
        assertNull(profileService.resolveJobQuery(""));
        assertNull(profileService.resolveJobQuery("   "));
        assertNull(profileService.resolveJobQuery(null));
    }
}
