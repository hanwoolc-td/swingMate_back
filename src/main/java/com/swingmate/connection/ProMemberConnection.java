package com.swingmate.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pro_member_connections")
public class ProMemberConnection {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID proId;

    @Column(nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant respondedAt;

    protected ProMemberConnection() {
    }

    public static ProMemberConnection request(UUID proId, UUID memberId, Instant now) {
        ProMemberConnection connection = new ProMemberConnection();
        connection.id = UUID.randomUUID();
        connection.proId = proId;
        connection.memberId = memberId;
        connection.status = ConnectionStatus.PENDING;
        connection.requestedAt = now;
        return connection;
    }

    /** 종료된 연결을 다시 요청하면 같은 행이 대기 상태로 돌아간다. */
    public void requestAgain(Instant now) {
        status = ConnectionStatus.PENDING;
        requestedAt = now;
        respondedAt = null;
    }

    public void approve(Instant now) {
        status = ConnectionStatus.ACTIVE;
        respondedAt = now;
    }

    public void reject(Instant now) {
        status = ConnectionStatus.ENDED;
        respondedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProId() {
        return proId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
