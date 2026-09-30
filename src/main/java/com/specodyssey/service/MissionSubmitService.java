package com.specodyssey.service;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.service.CodeCompileService.CompileCheck;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 일일 미션 "정답 입력하기" — 풀이 코드를 받아 컴파일되는지 확인하고, 통과하면 USER_DAILY_MISSION에 저장한다.
 * "실패" 버튼도 여기서 처리한다(완료 + 오답으로 표시).
 * 관련 요구사항: FR-53 완료 체크
 * 항상 세션의 본인 userId로만 미션을 찾는다(다른 사람 미션 id를 넣어도 찾지 못한다).
 */
public class MissionSubmitService {

    private static final Logger LOG = Logger.getLogger(MissionSubmitService.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int MAX_CODE_LENGTH = 50_000;

    public enum Status { SAVED, INVALID, COMPILE_ERROR, UNAVAILABLE }

    /** 제출 결과. SAVED가 아니면 message를 화면에 보여 준다. */
    public record SubmitResult(Status status, String message) {
        public boolean isSaved() {
            return status == Status.SAVED;
        }
    }

    private final MissionDao missionDao = new MissionDao();
    private final CodeCompileService compileService = new CodeCompileService();

    /** 본인 미션이 아니거나 없으면 null. */
    public DailyMissionViewDto findOwnMission(Long userId, Long missionId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return missionDao.findMissionById(conn, userId, missionId);
        }
    }

    /** 처음 들어왔을 때 골라 둘 언어 — 이전 제출 언어, 없으면 SQL 문제는 SQL, 나머지는 Java. */
    public static Language defaultLanguage(DailyMissionViewDto mission) {
        Language previous = Language.from(mission.getSubmittedLanguage());
        if (previous != null) {
            return previous;
        }
        return "SQL".equals(mission.getCategory()) ? Language.SQL : Language.JAVA;
    }

    /** "실패" 버튼 — 본인 미션이면 실패(완료 + 오답)로 표시하고 true, 없거나 남의 미션이면 false. */
    public boolean markFailed(Long userId, Long missionId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return missionDao.markFailed(conn, userId, missionId, LocalDateTime.now(ZONE)) == 1;
        }
    }

    public SubmitResult submit(Long userId, Long missionId, String languageCode, String code) throws SQLException {
        Language language = Language.from(languageCode);
        if (language == null) {
            return new SubmitResult(Status.INVALID, "언어를 선택해 주세요.");
        }
        if (code == null || code.isBlank()) {
            return new SubmitResult(Status.INVALID, "풀이 코드를 입력해 주세요.");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new SubmitResult(Status.INVALID, "코드가 너무 깁니다. (" + MAX_CODE_LENGTH + "자 이하)");
        }
        if (findOwnMission(userId, missionId) == null) {
            return new SubmitResult(Status.INVALID, "미션을 찾을 수 없습니다.");
        }

        // 외부 API 호출 동안 DB 커넥션을 잡고 있지 않도록 확인을 먼저 하고 저장은 따로 한다
        CompileCheck check;
        try {
            check = compileService.check(language, code);
        } catch (ExternalApiException e) {
            LOG.log(Level.WARNING, "코드 컴파일 확인 실패 — 저장하지 않고 안내만 합니다", e);
            return new SubmitResult(Status.UNAVAILABLE,
                    "지금은 컴파일 확인을 할 수 없습니다. 잠시 후 다시 시도해 주세요. 입력한 코드는 아래에 그대로 남아 있습니다.");
        }
        if (!check.passed()) {
            return new SubmitResult(Status.COMPILE_ERROR, check.message());
        }

        try (Connection conn = DBUtil.getConnection()) {
            int updated = missionDao.saveSubmission(conn, userId, missionId, language.name(), code,
                    LocalDateTime.now(ZONE));
            return updated == 1
                    ? new SubmitResult(Status.SAVED, null)
                    : new SubmitResult(Status.INVALID, "미션을 찾을 수 없습니다.");
        }
    }
}
