package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;

import java.sql.SQLException;
import java.util.List;

/**
 * 관리자 기준 데이터 관리(2026-10-06) — JOB·SKILL·CERTIFICATION·SKILL_ALIAS·JOB_REQUIRED_SKILL.
 * 사용자 개인 데이터(USER_*)나 로드맵은 범위 밖 — 그건 AdminUserService·AdminRoadmapService가 맡는다.
 */
public class AdminReferenceService {

    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final SkillAliasDao skillAliasDao = new SkillAliasDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();

    public List<JobDto> listJobs() throws SQLException {
        return jobDao.findAll();
    }

    public void saveJob(Long id, String jobName, String jobCategory, boolean popular) throws SQLException {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("직무명을 입력해주세요.");
        }
        if (id == null) {
            jobDao.insert(buildJob(jobName, jobCategory, popular));
        } else {
            jobDao.update(id, jobName.trim(), jobCategory, popular);
        }
    }

    public List<SkillDto> listSkills() throws SQLException {
        return skillDao.findAll();
    }

    public void saveSkill(Long id, String skillName, String category) throws SQLException {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("기술명을 입력해주세요.");
        }
        if (id == null) {
            SkillDto skill = new SkillDto();
            skill.setSkillName(skillName.trim());
            skill.setCategory(category);
            skillDao.insert(skill);
        } else {
            skillDao.update(id, skillName.trim(), category);
        }
    }

    public List<CertificationDto> listCertifications() throws SQLException {
        return certificationDao.findAll();
    }

    public void saveCertification(Long id, String certName, String issuer, String jobCategory,
            Integer difficultyLevel) throws SQLException {
        if (certName == null || certName.isBlank()) {
            throw new IllegalArgumentException("자격증명을 입력해주세요.");
        }
        if (id == null) {
            CertificationDto cert = new CertificationDto();
            cert.setCertName(certName.trim());
            cert.setIssuer(issuer);
            cert.setJobCategory(jobCategory);
            cert.setDifficultyLevel(difficultyLevel);
            certificationDao.insert(cert);
        } else {
            certificationDao.update(id, certName.trim(), issuer, jobCategory, difficultyLevel);
        }
    }

    public List<SkillAliasDto> listSkillAliases() throws SQLException {
        return skillAliasDao.findAll();
    }

    public void addSkillAlias(Long skillId, String aliasName) throws SQLException {
        if (skillId == null) {
            throw new IllegalArgumentException("기술을 선택해주세요.");
        }
        if (aliasName == null || aliasName.isBlank()) {
            throw new IllegalArgumentException("별칭을 입력해주세요.");
        }
        skillAliasDao.insert(skillId, aliasName.trim());
    }

    public void deleteSkillAlias(Long id) throws SQLException {
        skillAliasDao.softDelete(id);
    }

    public List<JobRequiredSkillDto> listRequiredSkills(Long jobId) throws SQLException {
        return jobRequiredSkillDao.findByJobId(jobId);
    }

    /**
     * 요구 수준으로 쓸 수 있는 값 — 로드맵 티어와 같은 4단계다 (2026-10-14).
     * 화면은 선택지로 막지만 POST 를 직접 보내면 뚫리므로 여기서 한 번 더 본다.
     * 예전에 자유 입력이던 때 한글("심화")이 그대로 저장돼 히트맵이 못 읽는 값이 생겼다.
     * 비워 두는 것(null)은 허용한다 — "아직 정하지 않았다"는 뜻이고 화면이 미지정으로 묶는다.
     */
    static final java.util.Set<String> REQUIRED_LEVELS =
            java.util.Set.of("ENTRY", "CORE", "ADVANCED", "EXPERT");

    static String checkRequiredLevel(String requiredLevel) {
        if (requiredLevel == null || requiredLevel.isBlank()) {
            return null;
        }
        String value = requiredLevel.trim().toUpperCase(java.util.Locale.ROOT);
        if (!REQUIRED_LEVELS.contains(value)) {
            throw new IllegalArgumentException(
                    "요구 수준은 입문(ENTRY) · 핵심(CORE) · 심화(ADVANCED) · 전문가(EXPERT) 중 하나여야 합니다.");
        }
        return value;
    }

    public void addRequiredSkill(Long jobId, Long skillId, String importance, String requiredLevel)
            throws SQLException {
        if (jobId == null || skillId == null) {
            throw new IllegalArgumentException("직무와 기술을 모두 선택해주세요.");
        }
        JobRequiredSkillDto item = new JobRequiredSkillDto();
        item.setJobId(jobId);
        item.setSkillId(skillId);
        item.setImportance(importance);
        item.setRequiredLevel(checkRequiredLevel(requiredLevel));
        item.setSource("MANUAL");
        item.setEstimated(false);
        jobRequiredSkillDao.insert(item);
    }

    public void updateRequiredSkill(Long id, String importance, String requiredLevel) throws SQLException {
        jobRequiredSkillDao.update(id, importance, checkRequiredLevel(requiredLevel));
    }

    public void deleteRequiredSkill(Long id) throws SQLException {
        jobRequiredSkillDao.softDelete(id);
    }

    private JobDto buildJob(String jobName, String jobCategory, boolean popular) {
        JobDto job = new JobDto();
        job.setJobName(jobName.trim());
        job.setJobCategory(jobCategory);
        job.setPopular(popular);
        return job;
    }
}
