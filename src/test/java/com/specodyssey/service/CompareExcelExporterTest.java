package com.specodyssey.service;

import com.specodyssey.dto.InterviewerCompareDto;
import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.InterviewerCompareDto.Criterion;
import com.specodyssey.dto.ShareViewDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 지원자 비교 엑셀 내용 단위테스트 — 한 사람 한 행, 공개한 값만, 숫자는 숫자 칸.
 */
class CompareExcelExporterTest {

    @Test
    void rows_지원자_한_명이_한_행이고_공개하지_않은_값은_비공개() {
        InterviewerCompareDto compare = new InterviewerCompareDto();
        compare.getCriteria().add(new Criterion(1L, "Java", 3));

        ShareViewDto full = new ShareViewDto();
        full.setScopeBasic(true);
        full.setScopeSkills(true);
        full.setScopeAge(true);
        full.setName("김철수");
        full.setMajor("컴퓨터공학");
        full.setGrade("4학년");
        full.setAge(25);
        full.getCertNames().addAll(List.of("정보처리기사", "SQLD"));
        full.setProjectCount(2);
        full.getSkills().addAll(List.of("Java", "Spring"));
        Applicant a = applicant("김철수", full);
        a.getMatches().add(true);
        a.setFitScore(100);

        ShareViewDto skillsOnly = new ShareViewDto();
        skillsOnly.setScopeSkills(true);
        Applicant b = applicant("지원자 2", skillsOnly);
        b.getMatches().add(false);
        b.setFitScore(0);

        Applicant stopped = new Applicant();
        stopped.setLabel("지원자 3");
        stopped.setAddedDate("2026-10-06");

        compare.getApplicants().addAll(List.of(a, b, stopped));
        List<List<Object>> rows = CompareExcelExporter.rows(compare);

        assertEquals(4, rows.size());
        assertEquals(List.of("순번", "지원자", "담은 날짜", "공유 상태", "전공", "학년", "희망 직무", "나이",
                "자격증", "자격증 수", "프로젝트 수", "Java (가중치 3)", "적합도 점수", "보유 기술", "성장 잠재력",
                "이력서 공개", "자소서 공개"), rows.get(0));
        assertEquals(Arrays.asList(1, "김철수", "2026-10-06", "공유 중", "컴퓨터공학", "4학년", null, 25,
                "정보처리기사, SQLD", 2, 2, "갖춤", 100, "Java, Spring", "비공개", "비공개", "비공개"), rows.get(1));
        assertEquals(Arrays.asList(2, "지원자 2", "2026-10-06", "공유 중", "비공개", "비공개", "비공개", "비공개",
                "비공개", "비공개", "비공개", "없음", 0, "", "비공개", "비공개", "비공개"), rows.get(2));
        // 머리 행과 칸 수가 같아야 엑셀에서 열이 어긋나지 않는다
        assertEquals(rows.get(0).size(), rows.get(3).size());
        assertEquals("공유 중단", rows.get(3).get(3));
        assertEquals("공유 중단", rows.get(3).get(rows.get(3).size() - 1));
    }

    @Test
    void toXlsx_엑셀_파일_바이트는_zip이다() {
        byte[] bytes = CompareExcelExporter.toXlsx(new InterviewerCompareDto());
        assertTrue(bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K', "xlsx는 zip(PK)으로 시작");
    }

    private static Applicant applicant(String label, ShareViewDto view) {
        Applicant a = new Applicant();
        a.setLabel(label);
        a.setAddedDate("2026-10-06");
        a.setView(view);
        return a;
    }
}
