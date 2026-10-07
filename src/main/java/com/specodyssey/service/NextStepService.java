package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSurveyAnswerDao;
import com.specodyssey.dto.UserDto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 다음에 할 일 안내. 관련 요구사항: FR-114 빈 상태(empty state)
 *
 * 갓 가입한 사람에게는 화면마다 "아직 없습니다"만 보였다 — 무엇을 해야 그게 채워지는지는 알려주지 않았다.
 * 여기서 사용자의 실제 상태를 읽어 여정의 어디까지 왔는지와 "지금 할 한 가지"를 계산한다.
 * 화면은 출력만 한다(claude.md).
 *
 * 순서는 핵심 여정 루프와 같다: 프로필 → 기술 → 직무 찾기 → 격차 분석 → 로드맵.
 * 앞 단계를 건너뛴 사람도 있어서(기존 가입자) 완료 여부는 단계마다 따로 본다 — 첫 미완료 단계가 "지금 할 일"이다.
 */
public class NextStepService {

    /** 안내 한 줄. done이면 체크 표시, current면 화면이 강조한다. */
    public static class Step {
        private final String title;
        private final String hint;
        private final String path;
        private final String actionLabel;
        private final boolean done;
        private boolean current;

        Step(String title, String hint, String path, String actionLabel, boolean done) {
            this.title = title;
            this.hint = hint;
            this.path = path;
            this.actionLabel = actionLabel;
            this.done = done;
        }

        public String getTitle() {
            return title;
        }

        public String getHint() {
            return hint;
        }

        public String getPath() {
            return path;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isCurrent() {
            return current;
        }
    }

    /** 여정 진행 상황 한 묶음. */
    public static class Guide {
        private final List<Step> steps;
        private final Step current;

        Guide(List<Step> steps, Step current) {
            this.steps = steps;
            this.current = current;
        }

        public List<Step> getSteps() {
            return steps;
        }

        /** 지금 할 한 가지. 전부 끝냈으면 null — 그때는 안내를 띄우지 않는다. */
        public Step getCurrent() {
            return current;
        }

        public int getDoneCount() {
            return (int) steps.stream().filter(Step::isDone).count();
        }

        public int getTotalCount() {
            return steps.size();
        }

        public int getPercent() {
            return steps.isEmpty() ? 0 : getDoneCount() * 100 / steps.size();
        }

        /** 전부 끝냈는지 — 화면이 안내 카드를 접을지 정한다. */
        public boolean isAllDone() {
            return current == null;
        }
    }

    private final UserDao userDao = new UserDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final UserSurveyAnswerDao surveyAnswerDao = new UserSurveyAnswerDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();

    /**
     * 세션의 사용자 정보는 프로필을 고친 직후에도 오래된 값일 수 있어 DB에서 다시 읽는다.
     * 호출은 한 화면에 한 번이라 조회 5번으로 묶어 둔다(목록을 세는 것뿐이라 가볍다).
     */
    public Guide load(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        if (user == null) {
            return new Guide(List.of(), null);
        }
        boolean hasBasic = hasBasicProfile(user);
        boolean hasSkills = !userSkillDao.findByUserId(userId).isEmpty();
        boolean hasJob = surveyAnswerDao.hasJobDiscoveryAnswer(userId) || user.getDesiredJobId() != null;
        boolean hasAnalysis = !gapAnalysisDao.findByUserId(userId).isEmpty();
        boolean hasRoadmap = roadmapDao.findPrimaryByUserId(userId) != null;
        return build(hasBasic, hasSkills, hasJob, hasAnalysis, hasRoadmap);
    }

    /** 전공·학년이 비어 있으면 추천이 전공을 반영할 수 없다 — 그래서 이 둘을 기본 정보의 기준으로 본다. */
    static boolean hasBasicProfile(UserDto user) {
        return isFilled(user.getMajor()) && isFilled(user.getGrade());
    }

    /** 상태만 받아 안내를 만든다 — DB 없이 규칙을 검사할 수 있게 분리했다. */
    static Guide build(boolean hasBasic, boolean hasSkills, boolean hasJob, boolean hasAnalysis, boolean hasRoadmap) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("프로필 기본 정보 채우기",
                "전공과 학년을 넣으면 추천이 전공을 반영합니다.",
                "/profile", "프로필 열기", hasBasic));
        steps.add(new Step("할 수 있는 기술 적기",
                "하나만 적어도 됩니다. 적은 기술을 기준으로 부족한 것을 찾습니다.",
                "/profile", "기술 추가하기", hasSkills));
        steps.add(new Step("목표 직무 정하기",
                "직무 찾기 설문을 풀면 맞는 직무를 추천해 드립니다. 이미 알고 있으면 프로필에서 바로 고르세요.",
                "/job-discovery", "직무 찾기", hasJob));
        steps.add(new Step("격차 분석 받기",
                "목표 직무가 요구하는 기술과 지금 가진 기술을 맞춰 봅니다.",
                "/discover", "분석 시작", hasAnalysis));
        steps.add(new Step("로드맵 만들기",
                "부족한 기술을 순서가 있는 길로 바꿉니다. 여기서부터 매일 걷게 됩니다.",
                "/roadmap", "로드맵 만들기", hasRoadmap));

        // 첫 미완료 단계가 "지금 할 일" — 앞 단계를 건너뛴 기존 가입자도 자기 자리에서 이어갈 수 있다
        Step current = null;
        for (Step step : steps) {
            if (!step.isDone()) {
                step.current = true;
                current = step;
                break;
            }
        }
        return new Guide(steps, current);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
