package com.swingmate.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tokenId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String platform;

    private String appVersion;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, UUID tokenId, UUID userId, UUID deviceId, Instant expiresAt,
                        String deviceName, String platform, String appVersion) {
        this.id = id;
        this.tokenId = tokenId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
        this.deviceName = deviceName;
        this.platform = platform;
        this.appVersion = appVersion;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
    }

    public boolean matches(UUID userId, UUID tokenId) {
        return this.userId.equals(userId) && this.tokenId.equals(tokenId);
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    public void rotate(UUID tokenId, Instant expiresAt) {
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
        this.lastUsedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTokenId() { return tokenId; }
    public UUID getUserId() { return userId; }
    public UUID getDeviceId() { return deviceId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getDeviceName() { return deviceName; }
    public String getPlatform() { return platform; }
    public String getAppVersion() { return appVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
