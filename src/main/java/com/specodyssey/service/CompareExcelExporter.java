package com.specodyssey.service;

import com.specodyssey.dto.InterviewerCompareDto;
import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.InterviewerCompareDto.Criterion;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.util.SimpleXlsxWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * 면접관 "지원자 비교"를 엑셀(.xlsx) 파일로 만든다. 관련 요구사항: FR-82 · 83
 * 지원자 한 명이 한 행이고, 순서는 화면에서 고른 정렬 기준을 따른다.
 * 화면과 같이 지원자가 공개한 값만 넣는다 — 공개하지 않은 칸은 "비공개", 공유가 중단된 지원자는 "공유 중단".
 * 순번·나이·개수·적합도는 숫자 칸으로 넣어 엑셀에서 정렬·계산할 수 있게 한다.
 * 열 너비·머리 행 고정·필터는 SimpleXlsxWriter가 맞춘다.
 */
public final class CompareExcelExporter {

    static final String SHEET_NAME = "지원자 비교";
    static final String HIDDEN = "비공개";
    static final String STOPPED = "공유 중단";

    private CompareExcelExporter() {
    }

    public static byte[] toXlsx(InterviewerCompareDto compare) {
        return SimpleXlsxWriter.write(SHEET_NAME, rows(compare));
    }

    /** 머리 행 + 지원자 행. 칸 값은 String 또는 Integer(숫자 칸). */
    static List<List<Object>> rows(InterviewerCompareDto compare) {
        List<List<Object>> rows = new ArrayList<>();
        List<Object> header = new ArrayList<>(List.of(
                "순번", "지원자", "담은 날짜", "내 검토 상태", "내 평점", "내 메모", "공유 상태", "전공", "학년", "희망 직무", "나이",
                "자격증", "자격증 수", "프로젝트 수"));
        for (Criterion c : compare.getCriteria()) {
            header.add(c.getSkillName() + " (가중치 " + c.getWeight() + ")");
        }
        header.addAll(List.of("적합도 점수", "보유 기술", "성장 잠재력", "이력서 공개", "자소서 공개"));
        rows.add(header);

        int order = 1;
        for (Applicant a : compare.getApplicants()) {
            rows.add(row(order++, a, compare.getCriteria().size(), header.size()));
        }
        return rows;
    }

    private static List<Object> row(int order, Applicant a, int criteriaCount, int columnCount) {
        List<Object> cells = new ArrayList<>();
        cells.add(order);
        cells.add(a.getLabel());
        cells.add(a.getAddedDate());
        // 면접관 본인의 검토 기록은 지원자가 공유를 멈춰도 남는다 — 공유 상태보다 앞에 둔다
        cells.add(a.getReviewStatusLabel());
        cells.add(a.getRating());
        cells.add(a.getMemo());
        ShareViewDto v = a.getView();
        if (v == null) {
            // 공유가 중단되면 아무 값도 읽을 수 없다 — 나머지 칸 전부 "공유 중단"
            while (cells.size() < columnCount) {
                cells.add(STOPPED);
            }
            return cells;
        }
        cells.add("공유 중");

        boolean basic = v.isScopeBasic();
        cells.add(basic ? v.getMajor() : HIDDEN);
        cells.add(basic ? v.getGrade() : HIDDEN);
        cells.add(basic ? v.getDesiredJobName() : HIDDEN);
        cells.add(v.isScopeAge() ? v.getAge() : HIDDEN);
        cells.add(basic ? String.join(", ", v.getCertNames()) : HIDDEN);
        cells.add(basic ? (Object) v.getCertNames().size() : HIDDEN);
        cells.add(basic ? (Object) v.getProjectCount() : HIDDEN);

        for (int i = 0; i < criteriaCount; i++) {
            if (!v.isScopeSkills()) {
                cells.add(HIDDEN);
            } else {
                boolean has = i < a.getMatches().size() && a.getMatches().get(i);
                String detail = i < a.getMatchDetails().size() ? a.getMatchDetails().get(i) : null;
                cells.add(!has ? "없음" : detail == null ? "갖춤" : "갖춤 (" + detail + ")");
            }
        }
        cells.add(v.isScopeSkills() ? a.getFitScore() : HIDDEN);
        cells.add(v.isScopeSkills() ? String.join(", ", v.getSkills()) : HIDDEN);
        cells.add(v.isScopeGrowth() ? a.getGrowthText() : HIDDEN);
        cells.add(v.isScopeResume() && v.getResumeFileName() != null ? "공개" : HIDDEN);
        cells.add(v.isScopeCoverLetter() && v.getCoverLetterFileName() != null ? "공개" : HIDDEN);
        return cells;
    }
}
