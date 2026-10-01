package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.ShareLinkViewLogDao.ViewStat;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkItemDto;
import com.specodyssey.util.DBUtil;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 면접관 공유 링크 발급·목록·중단·삭제. 관련 요구사항: FR-85 · 86, NFR-9
 */
public class ShareLinkService {

    // 화면의 "만료" 선택지와 같은 값이어야 한다 (share-links.jsp)
    private static final Set<Integer> ALLOWED_EXPIRY_DAYS = Set.of(7, 30, 90);
    private static final int LABEL_MAX_LENGTH = 50; // SHARE_LINK.label VARCHAR(50)
    private static final int TOKEN_BYTES = 32;      // Base64URL 43자 — SHARE_LINK.token VARCHAR(64) 안에 들어간다

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao viewLogDao = new ShareLinkViewLogDao();

    /**
     * @param expiryDays 만료까지 남은 일수. null이면 만료 없음
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public ShareLinkDto createLink(Long userId, String label, Integer expiryDays,
                                   boolean scopeBasic, boolean scopeSkills, boolean scopeGrowth) throws SQLException {
        return createLink(userId, label, expiryDays, scopeBasic, scopeSkills, scopeGrowth, false);
    }

    /**
     * @param scopeResume 이력서 파일 공개 — 연락처 같은 개인정보가 들어 있어 따로 고른 링크에서만 내려받을 수 있다
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public ShareLinkDto createLink(Long userId, String label, Integer expiryDays, boolean scopeBasic,
                                   boolean scopeSkills, boolean scopeGrowth, boolean scopeResume)
            throws SQLException {
        String trimmedLabel = (label == null || label.isBlank()) ? null : label.trim();
        if (trimmedLabel != null && trimmedLabel.length() > LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException("메모는 " + LABEL_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        if (expiryDays != null && !ALLOWED_EXPIRY_DAYS.contains(expiryDays)) {
            throw new IllegalArgumentException("만료 기간을 다시 선택해주세요.");
        }
        if (!scopeBasic && !scopeSkills && !scopeGrowth && !scopeResume) {
            throw new IllegalArgumentException("공개 범위를 하나 이상 선택해주세요.");
        }

        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken(generateToken());
        link.setActive(true);
        link.setExpiresAt(expiryDays == null ? null : LocalDateTime.now().plusDays(expiryDays));
        link.setScopeBasic(scopeBasic);
        link.setScopeSkills(scopeSkills);
        link.setScopeGrowth(scopeGrowth);
        link.setScopeResume(scopeResume);
        link.setLabel(trimmedLabel);
        link.setId(shareLinkDao.insert(link));
        return link;
    }

    /** 내 공유 링크 목록 — 최근에 만든 것부터. */
    public List<ShareLinkItemDto> listLinks(Long userId) throws SQLException {
        Map<Long, ViewStat> stats = viewLogDao.findViewStatsByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        List<ShareLinkItemDto> items = new ArrayList<>();
        for (ShareLinkDto link : shareLinkDao.findByUserId(userId)) {
            ShareLinkItemDto item = new ShareLinkItemDto();
            item.setId(link.getId());
            item.setToken(link.getToken());
            item.setLabel(link.getLabel());
            item.setStatus(statusOf(link, now));
            item.setExpiresDate(link.getExpiresAt() == null ? null : link.getExpiresAt().toLocalDate().toString());
            item.setScopeText(scopeText(link));
            ViewStat stat = stats.get(link.getId());
            if (stat != null) {
                item.setViewCount(stat.getViewCount());
                item.setLastViewedDate(stat.getLastViewedAt().toLocalDate().toString());
            }
            items.add(item);
        }
        return items;
    }

    // FR-86 공유 중단(active = false) · 다시 공유(active = true). 남의 링크 id면 아무 일도 일어나지 않는다.
    public void setActive(Long userId, Long linkId, boolean active) throws SQLException {
        shareLinkDao.updateActive(linkId, userId, active);
    }

    public void deleteLink(Long userId, Long linkId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            shareLinkDao.delete(conn, linkId, userId);
        }
    }

    // 만료가 중단보다 먼저다 — 만료된 링크는 다시 공유해도 열리지 않으므로 "다시 공유하기"를 보여주면 안 된다.
    static String statusOf(ShareLinkDto link, LocalDateTime now) {
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        return link.isActive() ? "ACTIVE" : "STOPPED";
    }

    private static String scopeText(ShareLinkDto link) {
        List<String> scopes = new ArrayList<>();
        if (link.isScopeBasic()) {
            scopes.add("기본 이력");
        }
        if (link.isScopeSkills()) {
            scopes.add("보유 기술 스택");
        }
        if (link.isScopeGrowth()) {
            scopes.add("성장 잠재력");
        }
        if (link.isScopeResume()) {
            scopes.add("이력서 파일");
        }
        return String.join(", ", scopes);
    }

    // NFR-9 추측 불가능한 토큰 — 256비트 난수를 URL에 그대로 넣을 수 있는 Base64URL로 만든다
    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
