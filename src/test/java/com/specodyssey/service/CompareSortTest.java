package com.specodyssey.service;

import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.ShareViewDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 면접관 나란히 보기 정렬 규칙 단위테스트 (FR-82). 공개하지 않은 값·공유 중단은 맨 뒤, 같으면 담은 순서 유지.
 */
class CompareSortTest {

    @Test
    void fromKey_모르는_값이면_담은_순서() {
        assertEquals(CompareSort.ADDED, CompareSort.fromKey(null));
        assertEquals(CompareSort.ADDED, CompareSort.fromKey("drop table"));
        assertEquals(CompareSort.FIT, CompareSort.fromKey("fit"));
    }

    @Test
    void ADDED_담은_순서_그대로() {
        List<Applicant> list = list(applicant("다", 30, 50, 1), applicant("가", 25, 90, 2));
        CompareSort.ADDED.sort(list);
        assertEquals(List.of("다", "가"), labels(list));
    }

    @Test
    void NAME_가나다순이고_이름_비공개는_뒤로() {
        Applicant hidden = applicant("지원자 1", 20, 10, 1);
        hidden.getView().setScopeBasic(false);
        List<Applicant> list = list(hidden, applicant("박민수", 30, 50, 2), applicant("김철수", 25, 90, 3));
        CompareSort.NAME.sort(list);
        assertEquals(List.of("김철수", "박민수", "지원자 1"), labels(list));
    }

    @Test
    void AGE_나이를_공개한_지원자만_나이순_나머지는_뒤로() {
        Applicant noAge = applicant("비공개", 20, 10, 1);
        noAge.getView().setScopeAge(false);
        List<Applicant> list = list(noAge, applicant("서른", 30, 50, 2), applicant("스물다섯", 25, 90, 3));

        CompareSort.AGE_ASC.sort(list);
        assertEquals(List.of("스물다섯", "서른", "비공개"), labels(list));

        CompareSort.AGE_DESC.sort(list);
        assertEquals(List.of("서른", "스물다섯", "비공개"), labels(list));
    }

    @Test
    void FIT_높은_순이고_점수_없음과_공유_중단은_뒤로_같으면_담은_순서() {
        Applicant stopped = new Applicant();
        stopped.setLabel("중단");
        List<Applicant> list = list(stopped, applicant("A", 30, 70, 1), applicant("B", 30, null, 2),
                applicant("C", 30, 90, 3), applicant("D", 30, 70, 4));
        CompareSort.FIT.sort(list);
        assertEquals(List.of("C", "A", "D", "중단", "B"), labels(list));
    }

    @Test
    void CERTS_PROJECTS_많은_순() {
        Applicant a = applicant("A", 30, 0, 1);
        a.getView().getCertNames().add("SQLD");
        a.getView().setProjectCount(1);
        Applicant b = applicant("B", 30, 0, 2);
        b.getView().getCertNames().addAll(List.of("정보처리기사", "SQLD"));
        b.getView().setProjectCount(0);
        List<Applicant> list = list(a, b);

        CompareSort.CERTS.sort(list);
        assertEquals(List.of("B", "A"), labels(list));
        CompareSort.PROJECTS.sort(list);
        assertEquals(List.of("A", "B"), labels(list));
    }

    @Test
    void RECENT_최근_담은_순() {
        List<Applicant> list = list(applicant("먼저", 30, 0, 1), applicant("나중", 30, 0, 5));
        CompareSort.RECENT.sort(list);
        assertEquals(List.of("나중", "먼저"), labels(list));
    }

    // addedDay: 담은 날(10월 n일) — 작을수록 먼저 담음
    private static Applicant applicant(String name, Integer age, Integer fit, int addedDay) {
        ShareViewDto view = new ShareViewDto();
        view.setScopeBasic(true);
        view.setScopeAge(true);
        view.setName(name);
        view.setAge(age);
        Applicant a = new Applicant();
        a.setLabel(name);
        a.setView(view);
        a.setFitScore(fit);
        a.setAddedAt(LocalDateTime.of(2026, 10, addedDay, 9, 0));
        return a;
    }

    private static List<Applicant> list(Applicant... applicants) {
        return new ArrayList<>(List.of(applicants));
    }

    private static List<String> labels(List<Applicant> list) {
        return list.stream().map(Applicant::getLabel).toList();
    }
}
