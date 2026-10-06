package com.specodyssey.service.discovery;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRecommendationDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SurveyQuestionDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSurveyAnswerDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRecommendationDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.SurveyQuestionDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSurveyAnswerDto;
import com.specodyssey.service.EmbeddingMatcher;
import com.specodyssey.service.SkillMatcher;
import com.specodyssey.service.discovery.JobDiscoveryScorer.JobCandidate;
import com.specodyssey.service.discovery.JobDiscoveryScorer.OwnedSkill;
import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.service.discovery.JobDiscoveryScorer.RequiredSkill;
import com.specodyssey.service.discovery.JobDiscoveryScorer.SurveyAnswer;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 직무 발굴. 관련 요구사항: FR-34 · 38 · 39
 * 설문 응답 + 보유 스펙(기술·프로젝트 기술 스택) + 전공(임베딩 역산, FR-38 ②)으로 후보 직무 3~5개를 추천하고,
 * 고른 직무를 격차 분석으로 넘긴다.
 *
 * 재응답 정책: 직무 발굴 설문은 다시 풀 수 있다. 관심이 바뀌거나 스펙이 늘면 추천도 달라져야 하기 때문이다.
 * 다시 풀면 응답은 덮어쓰고(upsert), 이전 추천은 논리 삭제한 뒤 새 추천을 저장한다 — 한 트랜잭션.
 *
 * 추천 이유는 RecommendationDescriber가 LLM 문장으로 다듬는다(2026-10-02 연결): {@link #describe}.
 * LLM이 실패해도 계산기가 만든 기본 문장이 남아 있어 화면이 멈추지 않는다(FR-111).
 */
public class JobDiscoveryService {

    public static final String SURVEY_TYPE = "JOB_DISCOVERY";

    private final SurveyQuestionDao questionDao = new SurveyQuestionDao();
    private final UserSurveyAnswerDao answerDao = new UserSurveyAnswerDao();
    private final JobRecommendationDao recommendationDao = new JobRecommendationDao();
    private final JobDao jobDao = new JobDao();
    private final JobRequiredSkillDao requiredSkillDao = new JobRequiredSkillDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserDao userDao = new UserDao();
    private final SkillMatcher skillMatcher;
    private final RecommendationDescriber describer;
    private final JobDiscoveryScorer scorer = new JobDiscoveryScorer();
    private final MajorAffinity majorAffinity = new MajorAffinity();

    public JobDiscoveryService() {
        // EmbeddingMatcher: TD-1 임베딩까지 붙은 최종 매처(2026-09-30). 정확 일치·SKILL_ALIAS·
        // 편집거리로 못 잡으면 로컬 임베딩 유사도까지 시도한다.
        this(new EmbeddingMatcher());
    }

    public JobDiscoveryService(SkillMatcher skillMatcher) {
        this(skillMatcher, new RecommendationDescriber());
    }

    public JobDiscoveryService(SkillMatcher skillMatcher, RecommendationDescriber describer) {
        this.skillMatcher = skillMatcher;
        this.describer = describer;
    }

    /** 설문 응답이 빠졌거나 범위를 벗어났을 때. 서블릿이 400으로 돌려준다. */
    public static class InvalidSurveyException extends Exception {
        public InvalidSurveyException(String message) {
            super(message);
        }
    }

    // ================= 조회 =================

    public List<SurveyQuestionDto> getQuestions() throws SQLException {
        return questionDao.findBySurveyType(SURVEY_TYPE);
    }

    /** 다시 풀 때 이전 응답을 미리 체크해 두려고 쓴다. key = question_id */
    /** 설문 문항이 바뀌어 아직 답하지 않은 문항 수(설문을 한 적 없는 사람은 0) — 다시 풀어 보라는 안내에 쓴다. */
    public int countNewQuestions(Long userId) throws SQLException {
        return answerDao.countUnansweredJobDiscoveryQuestions(userId);
    }

    public Map<Long, Integer> getMyAnswers(Long userId) throws SQLException {
        Map<Long, Integer> mine = new HashMap<>();
        for (UserSurveyAnswerDto a : answerDao.findByUserId(userId)) {
            mine.put(a.getQuestionId(), a.getAnswerValue());
        }
        return mine;
    }

    /** 화면용 추천 목록 (순위순). 아직 설문 전이면 빈 목록. */
    public List<RecommendationView> getRecommendations(Long userId) throws SQLException {
        Map<Long, String> jobNames = new HashMap<>();
        for (JobDto job : jobDao.findAll()) {
            jobNames.put(job.getId(), job.getJobName());
        }
        List<RecommendationView> views = new ArrayList<>();
        for (JobRecommendationDto r : recommendationDao.findByUserId(userId)) {
            views.add(new RecommendationView(r.getId(), r.getJobId(), jobNames.get(r.getJobId()),
                    r.getRankOrder(), r.getMatchReason(), r.isSelected()));
        }
        return views;
    }

    // ================= 설문 제출 → 추천 (FR-34 · 38) =================

    /**
     * @param answersByQuestionId key = SURVEY_QUESTION.id, value = 1~5
     * @return 저장된 추천 (순위순)
     */
    public List<Recommendation> submitSurvey(Long userId, Map<Long, Integer> answersByQuestionId)
            throws SQLException, InvalidSurveyException {
        List<SurveyQuestionDto> questions = getQuestions();
        if (questions.isEmpty()) {
            throw new IllegalStateException("직무 발굴 설문 문항이 없습니다. sql/05_seed_survey.sql을 실행하세요.");
        }
        List<SurveyAnswer> answers = validate(questions, answersByQuestionId);

        List<Recommendation> recommendations =
                scorer.recommend(answers, collectOwnedSkills(userId), loadJobCandidates(),
                        majorAffinity.score(findMajor(userId)));
        describe(recommendations);

        LocalDateTime now = LocalDateTime.now();
        TransactionUtil.runInTransaction(conn -> {
            for (SurveyQuestionDto q : questions) {
                UserSurveyAnswerDto a = new UserSurveyAnswerDto();
                a.setUserId(userId);
                a.setQuestionId(q.getId());
                a.setAnswerValue(answersByQuestionId.get(q.getId()));
                a.setAnsweredAt(now);
                answerDao.upsert(conn, a);
            }
            recommendationDao.softDeleteByUserId(conn, userId);
            for (Recommendation r : recommendations) {
                JobRecommendationDto dto = new JobRecommendationDto();
                dto.setUserId(userId);
                dto.setJobId(r.jobId);
                dto.setRankOrder(r.rankOrder);
                dto.setMatchReason(r.reason);
                recommendationDao.upsert(conn, dto);
            }
            return null;
        });
        return recommendations;
    }

    // ================= 후보 선택 → 격차 분석 (FR-39) =================

    /**
     * 고른 후보를 선택 상태로 바꾸고 그 job_id를 돌려준다. 서블릿은 이 값으로 격차 분석 화면으로 보낸다.
     * 내 추천이 아니거나 없는 id면 null — updateSelected가 void라 0행 갱신을 알 수 없어서 먼저 소유를 확인한다.
     * 이 선택을 곧 희망 직무 확정으로 본다 — 그래야 로드맵 등 "희망 직무가 있어야 보이는" 화면이
     * 직무 발굴을 거쳐온 사용자에게도 정상적으로 열린다(팀 합의, 2026-09-29).
     */
    public Long selectRecommendation(Long userId, Long recommendationId) throws SQLException {
        JobRecommendationDto target = null;
        for (JobRecommendationDto r : recommendationDao.findByUserId(userId)) {
            if (r.getId().equals(recommendationId)) {
                target = r;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        Long jobId = target.getJobId();
        TransactionUtil.runInTransaction(conn -> {
            recommendationDao.clearSelectedByUserId(conn, userId);
            recommendationDao.updateSelected(conn, recommendationId, userId, true);
            userDao.updateDesiredJob(conn, userId, jobId, "SET");
            return null;
        });
        return jobId;
    }

    // ================= 내부 =================

    private List<SurveyAnswer> validate(List<SurveyQuestionDto> questions, Map<Long, Integer> given)
            throws InvalidSurveyException {
        List<SurveyAnswer> answers = new ArrayList<>();
        for (SurveyQuestionDto q : questions) {
            Integer v = given.get(q.getId());
            if (v == null) {
                throw new InvalidSurveyException("모든 문항에 답해주세요.");
            }
            if (v < 1 || v > 5) {
                throw new InvalidSurveyException("응답은 1~5 중에서 골라주세요.");
            }
            answers.add(new SurveyAnswer(q.getJobCategoryHint(), v));
        }
        return answers;
    }

    /** 보유 기술 원문 + 프로젝트 tech_stack(쉼표·슬래시 구분)을 표준 스킬로 매칭한다. 매칭 실패는 버린다. */
    List<OwnedSkill> collectOwnedSkills(Long userId) throws SQLException {
        Map<String, String> raws = new LinkedHashMap<>(); // 소문자 키로 중복 제거, 값은 원문
        for (UserSkillDto s : userSkillDao.findByUserId(userId)) {
            addRaw(raws, s.getRawInput());
        }
        for (UserProjectDto p : userProjectDao.findByUserId(userId)) {
            if (p.getTechStack() != null) {
                for (String part : p.getTechStack().split("[,/]")) {
                    addRaw(raws, part);
                }
            }
        }
        List<OwnedSkill> owned = new ArrayList<>();
        for (String raw : raws.values()) {
            // "Java Spring"처럼 원문 하나에 기술이 여러 개 있으면 모두 보유로 본다 (2026-10-02)
            for (SkillMatcher.MatchResult m : skillMatcher.matchAll(raw)) {
                owned.add(new OwnedSkill(m.skillId(), raw, m.score()));
            }
        }
        return owned;
    }

    // FR-38 ② 전공 역산 재료. 프로필에 전공을 안 넣었으면 null → 전공 없이 추천한다
    private String findMajor(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        return user == null ? null : user.getMajor();
    }

    private void addRaw(Map<String, String> raws, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String trimmed = raw.trim();
        raws.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    private List<JobCandidate> loadJobCandidates() throws SQLException {
        List<JobCandidate> jobs = new ArrayList<>();
        for (JobDto job : jobDao.findAll()) {
            List<RequiredSkill> skills = new ArrayList<>();
            for (JobRequiredSkillDto rs : requiredSkillDao.findByJobId(job.getId())) {
                skills.add(new RequiredSkill(rs.getSkillId(), "REQUIRED".equals(rs.getImportance())));
            }
            jobs.add(new JobCandidate(job.getId(), job.getJobName(), job.getJobCategory(), skills));
        }
        return jobs;
    }

    /**
     * 추천 이유를 다듬는 자리 (FR-35 · 38의 "AI가 종합").
     * 실패하면 아무것도 하지 않고 넘어가서 계산기 기본 문장을 그대로 쓴다(FR-111).
     * summary_json(하는 일·필요 역량·전망)은 화면에서 아직 쓰지 않아 채우지 않는다.
     */
    private void describe(List<Recommendation> recommendations) {
        describer.describe(recommendations);
    }

    /** JSP 표시용. EL이 getter로 읽으므로 record 대신 클래스로 둔다(Tomcat 10.1의 EL 5.0은 record 접근자를 못 읽음). */
    public static class RecommendationView {
        private final Long id;
        private final Long jobId;
        private final String jobName;
        private final Integer rankOrder;
        private final String matchReason;
        private final boolean selected;

        public RecommendationView(Long id, Long jobId, String jobName, Integer rankOrder,
                                  String matchReason, boolean selected) {
            this.id = id;
            this.jobId = jobId;
            this.jobName = jobName;
            this.rankOrder = rankOrder;
            this.matchReason = matchReason;
            this.selected = selected;
        }

        public Long getId() { return id; }
        public Long getJobId() { return jobId; }
        public String getJobName() { return jobName; }
        public Integer getRankOrder() { return rankOrder; }
        public String getMatchReason() { return matchReason; }
        public boolean isSelected() { return selected; }
    }
}
