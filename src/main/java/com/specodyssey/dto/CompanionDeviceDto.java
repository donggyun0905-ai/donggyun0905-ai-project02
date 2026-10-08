package com.specodyssey.dto;

import java.time.LocalDateTime;

/** COMPANION_DEVICE 한 행 — 데스크톱 캐릭터가 연결된 PC 하나. 코드·토큰은 해시만 들고 다닌다. */
public class CompanionDeviceDto {

    private Long id;
    private Long userId;
    private String deviceName;
    private LocalDateTime connectedAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime signedOutAt; // 웹 로그아웃으로 쉬는 중 (sql/39)
    private String tokenHash;          // 브라우저 연결 쿠키 서명용 — 화면·응답으로 내보내지 않는다

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public LocalDateTime getSignedOutAt() {
        return signedOutAt;
    }

    public void setSignedOutAt(LocalDateTime signedOutAt) {
        this.signedOutAt = signedOutAt;
    }

    public boolean isSignedOut() {
        return signedOutAt != null;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}
