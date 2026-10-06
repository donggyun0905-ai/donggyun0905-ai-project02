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

    public void addRequiredSkill(Long jobId, Long skillId, String importance, String requiredLevel)
            throws SQLException {
        if (jobId == null || skillId == null) {
            throw new IllegalArgumentException("직무와 기술을 모두 선택해주세요.");
        }
        JobRequiredSkillDto item = new JobRequiredSkillDto();
        item.setJobId(jobId);
        item.setSkillId(skillId);
        item.setImportance(importance);
        item.setRequiredLevel(requiredLevel);
        item.setSource("MANUAL");
        item.setEstimated(false);
        jobRequiredSkillDao.insert(item);
    }

    public void updateRequiredSkill(Long id, String importance, String requiredLevel) throws SQLException {
        jobRequiredSkillDao.update(id, importance, requiredLevel);
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
