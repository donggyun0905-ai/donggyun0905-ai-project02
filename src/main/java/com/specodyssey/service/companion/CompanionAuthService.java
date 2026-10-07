package com.specodyssey.service.companion;

import com.specodyssey.dao.CompanionDeviceDao;
import com.specodyssey.dto.CompanionDeviceDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    /** 웹 "캐릭터 켜기" — 이 사용자의 일회용 코드 원문을 돌려준다 */
    public String issueCode(Long userId) throws SQLException {
        LocalDateTime now = now();
        deviceDao.deleteExpiredCodes(userId, now);
        String code = randomToken(CODE_BYTES);
        deviceDao.insertCode(userId, hash(code), now.plusSeconds(CODE_VALID_SECONDS));
        return code;
    }

    /**
     * exe가 받은 코드를 토큰으로 바꾼다. 만료됐거나 이미 쓴 코드면 null.
     */
    public Connected exchange(String code, String deviceName) throws SQLException {
        if (code == null || code.isBlank() || code.length() > 100) {
            return null;
        }
        String token = randomToken(TOKEN_BYTES);
        String name = deviceName == null || deviceName.isBlank() ? null
                : deviceName.strip().substring(0, Math.min(DEVICE_NAME_MAX, deviceName.strip().length()));
        Long userId = deviceDao.exchangeCode(hash(code), hash(token), name, now());
        return userId == null ? null : new Connected(userId, token);
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

    /** 본인 연결 해제 — 웹 프로필의 [연결 해제] 또는 exe의 "연결 해제" */
    public boolean revoke(Long userId, Long deviceId) throws SQLException {
        return deviceDao.revoke(deviceId, userId, now());
    }

    public List<CompanionDeviceDto> connectedDevices(Long userId) throws SQLException {
        return deviceDao.findConnectedByUserId(userId);
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
