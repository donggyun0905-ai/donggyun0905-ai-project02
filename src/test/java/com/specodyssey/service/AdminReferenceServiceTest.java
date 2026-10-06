package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** AdminReferenceService 통합테스트. 관리자가 직무·기술·자격증·별칭·직무별 요구 기술을 추가·수정·삭제한다. */
class AdminReferenceServiceTest {

    private final AdminReferenceService service = new AdminReferenceService();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final SkillAliasDao skillAliasDao = new SkillAliasDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();

    private Long jobId;
    private Long skillId;
    private Long certId;
    private Long aliasId;
    private Long requirementId;

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            if (requirementId != null) TestFixtures.hardDelete(conn, "JOB_REQUIRED_SKILL", requirementId);
            if (aliasId != null) TestFixtures.hardDelete(conn, "SKILL_ALIAS", aliasId);
            if (certId != null) TestFixtures.hardDelete(conn, "CERTIFICATION", certId);
            if (jobId != null) TestFixtures.hardDelete(conn, "JOB", jobId);
            if (skillId != null) TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    void 직무를_추가하고_수정할_수_있다() throws Exception {
        String name = "관리자테스트직무_" + System.nanoTime();
        service.saveJob(null, name, "백엔드", true);
        JobDto created = jobDao.findByName(name);
        jobId = created.getId();
        assertTrue(created.isPopular());

        service.saveJob(jobId, name, "프론트엔드", false);
        JobDto updated = jobDao.findById(jobId);
        assertEquals("프론트엔드", updated.getJobCategory());
        assertFalse(updated.isPopular());
    }

    @Test
    void 직무명이_비어있으면_예외를_던진다() {
        assertThrows(IllegalArgumentException.class, () -> service.saveJob(null, "  ", null, false));
    }

    @Test
    void 기술을_추가하고_수정할_수_있다() throws Exception {
        String name = "관리자테스트기술_" + System.nanoTime();
        service.saveSkill(null, name, "언어");
        SkillDto created = skillDao.findByName(name);
        skillId = created.getId();
        assertEquals("언어", created.getCategory());

        service.saveSkill(skillId, name, "프레임워크");
        assertEquals("프레임워크", skillDao.findById(skillId).getCategory());
    }

    @Test
    void 자격증을_추가하고_수정할_수_있다() throws Exception {
        String name = "관리자테스트자격증_" + System.nanoTime();
        service.saveCertification(null, name, "한국산업인력공단", "공통", 3);
        CertificationDto created = certificationDao.findByName(name);
        certId = created.getId();
        assertEquals(3, created.getDifficultyLevel());

        service.saveCertification(certId, name, "한국산업인력공단", "공통", 5);
        assertEquals(5, certificationDao.findById(certId).getDifficultyLevel());
    }

    @Test
    void 기술_별칭을_추가하고_삭제할_수_있다() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "별칭테스트기술_" + System.nanoTime());
        }
        String alias = "별칭테스트_" + System.nanoTime();
        service.addSkillAlias(skillId, alias);
        SkillAliasDto found = skillAliasDao.findByAliasName(alias);
        assertNotNull(found);
        aliasId = found.getId();
        assertEquals("MANUAL", found.getMatchType());

        service.deleteSkillAlias(aliasId);
        assertNull(skillAliasDao.findByAliasName(alias));
        // deleteSkillAlias는 논리 삭제라 행은 남아있다 — tearDown이 물리 삭제할 수 있게 aliasId는 그대로 둔다.
    }

    @Test
    void 직무별_요구_기술을_추가_수정_삭제할_수_있다() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "요구기술테스트_" + System.nanoTime());
        }
        String jobName = "요구기술테스트직무_" + System.nanoTime();
        service.saveJob(null, jobName, null, false);
        // 정확한 이름으로 찾는다 — 접두어로만 찾으면(findAll + startsWith) 과거에 정리 안 되고 남은
        // 같은 접두어의 행을 집어올 수 있다(실제로 겪음: 테스트가 한 번 실패해서 tearDown이 못 돈 뒤
        // 다음 실행에서 findFirst()가 오래된 행을 집었다).
        jobId = jobDao.findByName(jobName).getId();

        service.addRequiredSkill(jobId, skillId, "PREFERRED", "기초");
        List<JobRequiredSkillDto> list = jobRequiredSkillDao.findByJobId(jobId);
        assertEquals(1, list.size());
        requirementId = list.get(0).getId();
        assertEquals("PREFERRED", list.get(0).getImportance());

        service.updateRequiredSkill(requirementId, "REQUIRED", "심화");
        JobRequiredSkillDto updated = jobRequiredSkillDao.findByJobId(jobId).get(0);
        assertEquals("REQUIRED", updated.getImportance());
        assertEquals("심화", updated.getRequiredLevel());

        service.deleteRequiredSkill(requirementId);
        assertTrue(jobRequiredSkillDao.findByJobId(jobId).isEmpty());
        // deleteRequiredSkill도 논리 삭제라 행은 남아있다 — tearDown이 물리 삭제할 수 있게 그대로 둔다.
    }
}
