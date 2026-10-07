package com.specodyssey.service;

import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.UserEducationDto;
import com.specodyssey.dto.UserProjectDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 면접관 검토 상태·학력·팀 정보 입력 규칙 — DB 없이 도는 테스트. */
class InterviewerReviewRulesTest {

    @Test
    void 검토_상태별로_세고_고른_상태만_남긴다() {
        List<Applicant> list = new ArrayList<>(List.of(applicant("PASS"), applicant("REVIEWING"), applicant("PASS")));

        Map<String, Integer> counts = InterviewerService.countByReviewStatus(list);
        assertEquals(3, counts.get("ALL"));
        assertEquals(2, counts.get("PASS"));
        assertEquals(0, counts.get("FAIL"));

        InterviewerService.filterByReviewStatus(list, null);
        assertEquals(3, list.size());
        InterviewerService.filterByReviewStatus(list, "PASS");
        assertEquals(2, list.size());
    }

    @Test
    void 새로_담은_지원자는_검토_중이다() {
        Applicant a = new Applicant();
        assertEquals("REVIEWING", a.getReviewStatus());
        assertEquals("검토 중", a.getReviewStatusLabel());
    }

    @Test
    void 학력은_학교와_상태가_필수이고_학점은_만점과_함께() {
        assertThrows(IllegalArgumentException.class, () -> EducationService.validate(education(" ", "ENROLLED", null, null)));
        assertThrows(IllegalArgumentException.class, () -> EducationService.validate(education("한국대", "DROPPED", null, null)));
        assertThrows(IllegalArgumentException.class, () -> EducationService.validate(education("한국대", "GRADUATED", "3.8", null)));
        assertThrows(IllegalArgumentException.class, () -> EducationService.validate(education("한국대", "GRADUATED", "4.6", "4.5")));
        assertThrows(IllegalArgumentException.class, () -> EducationService.validate(education("한국대", "GRADUATED", "3.8", "5.0")));

        UserEducationDto ok = education("  한국대  ", "EXPECTED", "3.82", "4.50");
        assertDoesNotThrow(() -> EducationService.validate(ok));
        assertEquals("한국대", ok.getSchoolName());
        assertEquals("3.82 / 4.5", ok.getGpaText());
        assertEquals("졸업 예정", ok.getStatusLabel());

        // 학점이 없으면 만점도 저장하지 않는다
        UserEducationDto noGpa = education("한국대", "ENROLLED", null, "4.3");
        EducationService.validate(noGpa);
        assertNull(noGpa.getGpaMax());
        assertNull(noGpa.getGpaText());
    }

    @Test
    void 팀_인원은_1에서_100명() {
        assertThrows(IllegalArgumentException.class, () -> ProfileService.validateTeamInfo(project(0, null)));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.validateTeamInfo(project(101, null)));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.validateTeamInfo(project(3, "가".repeat(101))));
        assertDoesNotThrow(() -> ProfileService.validateTeamInfo(project(1, "혼자 전부")));
        assertDoesNotThrow(() -> ProfileService.validateTeamInfo(project(null, null)));
    }

    private static Applicant applicant(String status) {
        Applicant a = new Applicant();
        a.setReviewStatus(status);
        return a;
    }

    private static UserEducationDto education(String school, String status, String gpa, String gpaMax) {
        UserEducationDto e = new UserEducationDto();
        e.setSchoolName(school);
        e.setGraduationStatus(status);
        e.setGpa(gpa == null ? null : new BigDecimal(gpa));
        e.setGpaMax(gpaMax == null ? null : new BigDecimal(gpaMax));
        return e;
    }

    private static UserProjectDto project(Integer teamSize, String role) {
        UserProjectDto p = new UserProjectDto();
        p.setTeamSize(teamSize);
        p.setMyRole(role);
        return p;
    }
}
