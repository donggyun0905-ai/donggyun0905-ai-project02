package com.specodyssey.service;

import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.ShareViewDto;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 면접관 "지원자 비교 > 나란히 보기"의 정렬 기준. 관련 요구사항: FR-82
 * 정렬에는 지원자가 공개한 값만 쓴다 — 공개하지 않았거나(나이·기본 이력·기술 스택 비공개) 공유가 중단돼 값이 없는
 * 지원자는 기준과 상관없이 맨 뒤로 보낸다. 값이 같으면 담은 순서를 유지한다(List.sort는 안정 정렬).
 */
public enum CompareSort {
    ADDED("added", "담은 순서"),
    RECENT("recent", "최근 담은 순"),
    NAME("name", "이름순"),
    AGE_ASC("age", "나이 어린 순"),
    AGE_DESC("ageDesc", "나이 많은 순"),
    FIT("fit", "적합도 높은 순"),
    CERTS("certs", "자격증 많은 순"),
    PROJECTS("projects", "프로젝트 많은 순");

    private final String key;
    private final String label;

    CompareSort(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** 화면에서 넘어온 값. 모르는 값이면 기본(담은 순서). */
    public static CompareSort fromKey(String key) {
        for (CompareSort sort : values()) {
            if (sort.key.equals(key)) {
                return sort;
            }
        }
        return ADDED;
    }

    /** 담은 순서로 들어온 목록을 이 기준으로 제자리 정렬한다. */
    public void sort(List<Applicant> applicants) {
        Comparator<Applicant> comparator = comparator();
        if (comparator != null) {
            applicants.sort(comparator);
        }
    }

    private Comparator<Applicant> comparator() {
        return switch (this) {
            case ADDED -> null;
            case RECENT -> by(Applicant::getAddedAt, Comparator.<java.time.LocalDateTime>reverseOrder());
            case NAME -> by(CompareSort::disclosedName, KoreanOrder.COLLATOR::compare);
            case AGE_ASC -> by(CompareSort::disclosedAge, Comparator.<Integer>naturalOrder());
            case AGE_DESC -> by(CompareSort::disclosedAge, Comparator.<Integer>reverseOrder());
            case FIT -> by(Applicant::getFitScore, Comparator.<Integer>reverseOrder());
            case CERTS -> by(CompareSort::certCount, Comparator.<Integer>reverseOrder());
            case PROJECTS -> by(CompareSort::projectCount, Comparator.<Integer>reverseOrder());
        };
    }

    // 값이 없는(공개 안 함·공유 중단) 지원자는 맨 뒤
    private static <K> Comparator<Applicant> by(Function<Applicant, K> key, Comparator<K> order) {
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }

    // 이름은 기본 이력을 공개했을 때만 — 비공개면 화면에 "지원자 N"으로 나오므로 이름순에서는 뒤로
    private static String disclosedName(Applicant a) {
        ShareViewDto v = a.getView();
        return v != null && v.isScopeBasic() && v.getName() != null && !v.getName().isBlank() ? v.getName().strip() : null;
    }

    private static Integer disclosedAge(Applicant a) {
        ShareViewDto v = a.getView();
        return v != null && v.isScopeAge() ? v.getAge() : null;
    }

    private static Integer certCount(Applicant a) {
        ShareViewDto v = a.getView();
        return v != null && v.isScopeBasic() ? v.getCertNames().size() : null;
    }

    private static Integer projectCount(Applicant a) {
        ShareViewDto v = a.getView();
        return v != null && v.isScopeBasic() ? v.getProjectCount() : null;
    }

    // 한글 이름 가나다순 — enum 상수가 만들어질 때 쓰지 않도록 별도 클래스에 둔다
    private static final class KoreanOrder {
        static final Collator COLLATOR = Collator.getInstance(Locale.KOREAN);
    }
}
