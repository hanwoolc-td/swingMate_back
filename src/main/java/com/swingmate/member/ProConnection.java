package com.swingmate.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_pro_connections")
@IdClass(ProConnection.Key.class)
public class ProConnection {

    @Id
    private UUID memberId;

    @Id
    private UUID proId;

    @Column(nullable = false)
    private Instant connectedAt;

    protected ProConnection() {
    }

    public ProConnection(UUID memberId, UUID proId) {
        this.memberId = memberId;
        this.proId = proId;
        this.connectedAt = Instant.now();
    }

    public record Key(UUID memberId, UUID proId) implements Serializable {
    }
}
