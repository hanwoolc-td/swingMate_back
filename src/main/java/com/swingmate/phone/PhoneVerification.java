package com.swingmate.phone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private int attemptCount;
    private int resendCount;
    private Instant verifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected PhoneVerification() {
    }

    public PhoneVerification(UUID id, UUID userId, String phoneNumber, String purpose, String codeHash,
                             Instant createdAt, Instant expiresAt, int resendCount) {
        this.id = id;
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.resendCount = resendCount;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isAttemptsExceeded() {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void markVerified(Instant now) {
        verifiedAt = now;
    }

    /** 재발송 시 이전 인증번호는 더 이상 쓸 수 없게 한다. */
    public void expire(Instant now) {
        if (expiresAt.isAfter(now)) {
            expiresAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getResendCount() {
        return resendCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
