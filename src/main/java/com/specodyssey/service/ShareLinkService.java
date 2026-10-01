package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.util.TransactionUtil;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * 면접관 공유 링크 발급·관리. 관련 요구사항: FR-85 · 86, NFR-9
 * 지원자가 로그인한 상태에서 직접 발급·공개범위 조정·공유 중단하는 본인 소유 리소스.
 */
public class ShareLinkService {

    // CLAUDE.md 보안 원칙: "면접관 공유 링크 토큰은 추측 불가능한 랜덤 문자열(SecureRandom), 읽기 전용".
    // 256비트(32바이트)를 URL-safe Base64로 인코딩 — URL에 그대로 넣어도 안전한 문자만 나온다.
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao shareLinkViewLogDao = new ShareLinkViewLogDao();

    // JSTL fmt:formatDate는 java.util.Date 전용이라 java.time 타입을 못 받는다(claude.md: JSP에
    // 계산 로직 금지 — 날짜 포맷도 계산으로 보고 여기서 문자열로 미리 만들어 넘긴다).
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 화면에 보여줄 링크 1건 — 열람 횟수·최근 열람 시각을 같이 계산해서 묶어준다. */
    public static final class ShareLinkView {
        private final ShareLinkDto link;
        private final int viewCount;
        private final LocalDateTime lastViewedAt;

        public ShareLinkView(ShareLinkDto link, int viewCount, LocalDateTime lastViewedAt) {
            this.link = link;
            this.viewCount = viewCount;
            this.lastViewedAt = lastViewedAt;
        }

        public ShareLinkDto getLink() {
            return link;
        }

        public int getViewCount() {
            return viewCount;
        }

        public String getLastViewedAtDisplay() {
            return lastViewedAt == null ? null : lastViewedAt.format(DATE_FORMAT);
        }

        public String getExpiresAtDisplay() {
            return link.getExpiresAt() == null ? null : link.getExpiresAt().format(DATE_FORMAT);
        }

        // JSP에서 상태 칩을 고르기 쉽게 미리 계산해서 노출 — "공유 중" / "공유 중단됨" / "만료됨".
        public String getStatus() {
            if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
                return "EXPIRED";
            }
            return link.isActive() ? "ACTIVE" : "PAUSED";
        }
    }

    public ShareLinkDto issue(Long userId, String label, Integer expiresInDays, boolean scopeBasic,
            boolean scopeSkills, boolean scopeGrowth) throws SQLException {
        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken(generateToken());
        link.setActive(true);
        link.setExpiresAt(expiresInDays == null ? null : LocalDateTime.now().plusDays(expiresInDays));
        link.setScopeBasic(scopeBasic);
        link.setScopeSkills(scopeSkills);
        link.setScopeGrowth(scopeGrowth);
        link.setLabel(label);
        Long id = shareLinkDao.insert(link);
        link.setId(id);
        return link;
    }

    public List<ShareLinkView> listMine(Long userId) throws SQLException {
        List<ShareLinkDto> links = shareLinkDao.findByUserId(userId);
        List<ShareLinkView> views = new ArrayList<>();
        for (ShareLinkDto link : links) {
            List<ShareLinkViewLogDto> logs = shareLinkViewLogDao.findByShareLinkId(link.getId());
            LocalDateTime lastViewedAt = logs.stream()
                    .map(ShareLinkViewLogDto::getViewedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            views.add(new ShareLinkView(link, logs.size(), lastViewedAt));
        }
        return views;
    }

    public void setActive(Long userId, Long linkId, boolean active) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            shareLinkDao.updateActive(conn, linkId, userId, active);
            return null;
        });
    }

    public void delete(Long userId, Long linkId) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            shareLinkDao.delete(conn, linkId, userId);
            return null;
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
