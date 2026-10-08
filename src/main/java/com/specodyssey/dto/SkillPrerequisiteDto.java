package com.specodyssey.dto;

/** SKILL_PREREQUISITE 한 줄 — "skill을 하기 전에 prereqSkill". 이름은 관리자 화면 표시용. */
public class SkillPrerequisiteDto {

    private Long id;
    private Long skillId;
    private Long prereqSkillId;
    private String skillName;
    private String prereqSkillName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public Long getPrereqSkillId() {
        return prereqSkillId;
    }

    public void setPrereqSkillId(Long prereqSkillId) {
        this.prereqSkillId = prereqSkillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getPrereqSkillName() {
        return prereqSkillName;
    }

    public void setPrereqSkillName(String prereqSkillName) {
        this.prereqSkillName = prereqSkillName;
    }
}
