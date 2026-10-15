package com.swingmate.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 회원이 프로에게 보여주는 일회용 연결 코드. */
@Entity
@Table(name = "connection_codes")
public class ConnectionCode {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected ConnectionCode() {
    }

    public ConnectionCode(UUID memberId, String code, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.code = code;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
