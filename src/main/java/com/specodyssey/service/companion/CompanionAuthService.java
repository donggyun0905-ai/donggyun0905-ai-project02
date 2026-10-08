package com.specodyssey.service.companion;

import com.specodyssey.dao.CompanionDeviceDao;
import com.specodyssey.dto.CompanionDeviceDto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 데스크톱 캐릭터 연결 — 웹 로그인 사용자에게 일회용 코드를 주고, exe가 그 코드를 캐릭터 전용 토큰으로 바꾼다.
 * 웹 세션(쿠키)은 exe로 넘기지 않는다: 주소에 남으면 계정 전체가 노출되고, 세션이 끝나면 캐릭터도 끊기기 때문.
 * 코드·토큰은 SecureRandom으로 만들고 DB에는 SHA-256 해시만 둔다 (DB가 새어도 토큰으로 쓸 수 없게).
 *
 * 한 PC = 한 연결 (2026-10-08): 캐릭터가 다시 연결할 때 들고 있던 예전 토큰을 같이 보내면 그 연결을 끊는다.
 * 계정 따라가기 (2026-10-08): 연결을 만든 브라우저에 "이 PC의 캐릭터" 쿠키를 남긴다({@link #browserLink}).
 * 그 브라우저에서 다른 계정으로 로그인하면 캐릭터가 그 계정으로 옮겨 가고, 로그아웃하면 쉬는 상태가 된다 —
 * "캐릭터 연결"을 다시 누르지 않아도 된다. 쿠키 값은 행 id + 토큰 해시로 만든 서명이라 DB 없이는 만들 수 없다.
 */
public class CompanionAuthService {

    /** 일회용 코드 유효 시간 — 버튼을 누르고 exe가 켜질 때까지 */
    static final int CODE_VALID_SECONDS = 60;
    private static final int CODE_BYTES = 24;
    private static final int TOKEN_BYTES = 32;
    private static final int DEVICE_NAME_MAX = 100;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompanionDeviceDao deviceDao;

    public CompanionAuthService() {
        this(new CompanionDeviceDao());
    }

    CompanionAuthService(CompanionDeviceDao deviceDao) {
        this.deviceDao = deviceDao;
    }

    /** 결과 — 토큰 원문은 이때 한 번만 exe에 건넨다 */
    public record Connected(Long userId, String token) {
    }

    /** 일회용 코드 원문 + 그 코드가 든 행 id (exe가 교환하면 이 행이 곧 그 PC의 연결이 된다) */
    public record Issued(String code, Long deviceId) {
    }

    /** 웹 "캐릭터 연결" — 이 사용자의 일회용 코드 원문을 돌려준다 */
    public String issueCode(Long userId) throws SQLException {
        return issue(userId).code();
    }

    /** 웹 "캐릭터 연결" — 코드와 행 id. 행 id는 연결이 끝난 뒤 이 브라우저에 쿠키를 남길 때 쓴다 */
    public Issued issue(Long userId) throws SQLException {
        LocalDateTime now = now();
        deviceDao.deleteExpiredCodes(userId, now);
        String code = randomToken(CODE_BYTES);
        Long id = deviceDao.insertCode(userId, hash(code), now.plusSeconds(CODE_VALID_SECONDS));
        return new Issued(code, id);
    }

    /**
     * exe가 받은 코드를 토큰으로 바꾼다. 만료됐거나 이미 쓴 코드면 null.
     */
    public Connected exchange(String code, String deviceName) throws SQLException {
        return exchange(code, deviceName, null);
    }

    /**
     * @param previousToken 이 PC의 캐릭터가 들고 있던 예전 토큰 — 있으면 새 연결이 된 뒤 그 연결을 끊는다.
     *                      같은 PC에서 "캐릭터 연결"을 여러 번 눌러도 연결이 하나만 남게 (계정이 달라도).
     */
    public Connected exchange(String code, String deviceName, String previousToken) throws SQLException {
        if (code == null || code.isBlank() || code.length() > 100) {
            return null;
        }
        String token = randomToken(TOKEN_BYTES);
        String name = deviceName == null || deviceName.isBlank() ? null
                : deviceName.strip().substring(0, Math.min(DEVICE_NAME_MAX, deviceName.strip().length()));
        LocalDateTime now = now();
        Long userId = deviceDao.exchangeCode(hash(code), hash(token), name, now);
        if (userId == null) {
            return null; // 코드가 틀렸으면 예전 연결도 그대로 둔다
        }
        if (previousToken != null && !previousToken.isBlank() && previousToken.length() <= 100) {
            deviceDao.revokeByTokenHash(hash(previousToken), now);
        }
        return new Connected(userId, token);
    }

    /** 토큰 → 연결된 PC. 없거나 해제됐으면 null. 쓸 때마다 마지막 사용 시각을 남긴다. */
    public CompanionDeviceDto authenticate(String token) throws SQLException {
        if (token == null || token.isBlank() || token.length() > 100) {
            return null;
        }
        CompanionDeviceDto device = deviceDao.findActiveByTokenHash(hash(token));
        if (device != null) {
            deviceDao.touch(device.getId(), now());
        }
        return device;
    }

    /** authenticate와 같지만 마지막 사용 시각을 남기지 않는다 — 2초마다 묻는 /whoami용 (DB에 쓰지 않게) */
    public CompanionDeviceDto peek(String token) throws SQLException {
        if (token == null || token.isBlank() || token.length() > 100) {
            return null;
        }
        return deviceDao.findActiveByTokenHash(hash(token));
    }

    /** 본인 연결 해제 — 웹 프로필의 [연결 해제] 또는 exe의 "연결 해제" */
    public boolean revoke(Long userId, Long deviceId) throws SQLException {
        return deviceDao.revoke(deviceId, userId, now());
    }

    public List<CompanionDeviceDto> connectedDevices(Long userId) throws SQLException {
        return deviceDao.findConnectedByUserId(userId);
    }

    // ---------------------------------------------------------------- 브라우저 ↔ PC 캐릭터

    /**
     * 이 브라우저에 남길 "이 PC의 캐릭터" 쿠키 값. 아직 토큰으로 안 바꿨거나 해제된 연결이면 null.
     * 값 = 행 id + "." + HMAC-SHA256(토큰 해시, id) — 토큰 해시는 DB에만 있어 쿠키를 지어낼 수 없다.
     */
    public String browserLink(Long deviceId) throws SQLException {
        CompanionDeviceDto d = deviceId == null ? null : deviceDao.findById(deviceId);
        if (!linkable(d)) {
            return null;
        }
        return d.getId() + "." + sign(d.getTokenHash(), d.getId());
    }

    /** 쿠키 값 → 그 PC의 연결. 서명이 틀렸거나 해제된 연결이면 null */
    public CompanionDeviceDto deviceForBrowserLink(String value) throws SQLException {
        if (value == null || value.length() > 200) {
            return null;
        }
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        Long id;
        try {
            id = Long.valueOf(value.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
        CompanionDeviceDto d = deviceDao.findById(id);
        if (!linkable(d)) {
            return null;
        }
        byte[] expected = sign(d.getTokenHash(), id).getBytes(StandardCharsets.US_ASCII);
        byte[] given = value.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given) ? d : null;
    }

    /**
     * 그 PC 브라우저에서 로그인했다 — 캐릭터를 이 계정으로 옮긴다. 캐릭터를 쓰지 않는 계정(면접관·관리자)이면 쉬게 한다.
     * @return 캐릭터가 이 계정으로 옮겨 갔으면 true
     */
    public boolean onLogin(String browserLink, Long userId, boolean usesCharacter) throws SQLException {
        CompanionDeviceDto d = deviceForBrowserLink(browserLink);
        if (d == null) {
            return false;
        }
        if (!usesCharacter) {
            deviceDao.signOut(d.getId(), now());
            return false;
        }
        return deviceDao.switchUser(d.getId(), userId);
    }

    /** 그 PC 브라우저에서 로그아웃했다 — 캐릭터는 연결을 둔 채 쉰다 */
    public boolean onLogout(String browserLink) throws SQLException {
        CompanionDeviceDto d = deviceForBrowserLink(browserLink);
        return d != null && deviceDao.signOut(d.getId(), now());
    }

    private static boolean linkable(CompanionDeviceDto d) {
        return d != null && d.getTokenHash() != null && d.getRevokedAt() == null;
    }

    static String sign(String tokenHash, Long deviceId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenHash.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            byte[] out = mac.doFinal(("browser-link:" + deviceId).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다", e);
        }
    }

    // ----------------------------------------------------------------

    static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZONE).withNano(0);
    }
}
