package com.specodyssey.service;

import java.util.Set;

/**
 * 회원가입과 내 프로필이 함께 쓰는 인적 사항 입력값 — 이름·나이·구분·학년.
 * 관련 요구사항: FR-11 · 21
 * 만들 때 검증까지 끝낸다. 학년은 구분이 학생일 때만 값이 있다.
 */
public final class PersonalInfo {

    public static final String STUDENT = "STUDENT";
    private static final Set<String> CAREER_STATUSES = Set.of(STUDENT, "JOB_SEEKER", "EMPLOYED");

    private static final int NAME_MAX_LENGTH = 50;  // USERS.name VARCHAR(50)
    private static final int GRADE_MAX_LENGTH = 20; // USERS.grade VARCHAR(20)
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

    private final String name;
    private final Integer age;
    private final String careerStatus;
    private final String grade;

    private PersonalInfo(String name, Integer age, String careerStatus, String grade) {
        this.name = name;
        this.age = age;
        this.careerStatus = careerStatus;
        this.grade = grade;
    }

    /**
     * 지원자용 — 이름·나이·구분은 필수, 학년은 학생일 때 필수.
     * @param ageInput 화면에서 받은 나이 문자열
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public static PersonalInfo of(String name, String ageInput, String careerStatus, String grade) {
        String validName = requireName(name);

        int age;
        try {
            age = Integer.parseInt(ageInput == null ? "" : ageInput.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("나이를 숫자로 입력해주세요.");
        }
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new IllegalArgumentException("나이는 " + MIN_AGE + "~" + MAX_AGE + " 사이로 입력해주세요.");
        }

        if (careerStatus == null || !CAREER_STATUSES.contains(careerStatus)) {
            throw new IllegalArgumentException("학생·취준생·직장인 중 하나를 선택해주세요.");
        }

        String validGrade = null;
        if (STUDENT.equals(careerStatus)) {
            if (grade == null || grade.isBlank()) {
                throw new IllegalArgumentException("학년을 입력해주세요.");
            }
            validGrade = grade.trim();
            if (validGrade.length() > GRADE_MAX_LENGTH) {
                throw new IllegalArgumentException("학년은 " + GRADE_MAX_LENGTH + "자 이내로 입력해주세요.");
            }
        }
        return new PersonalInfo(validName, age, careerStatus, validGrade);
    }

    /**
     * 면접관용 — 이름만 받는다.
     * @throws IllegalArgumentException 이름이 비었거나 너무 긴 경우
     */
    public static PersonalInfo nameOnly(String name) {
        return new PersonalInfo(requireName(name), null, null, null);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("이름은 " + NAME_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        return trimmed;
    }

    public String getName() {
        return name;
    }

    public Integer getAge() {
        return age;
    }

    public String getCareerStatus() {
        return careerStatus;
    }

    public String getGrade() {
        return grade;
    }
}
