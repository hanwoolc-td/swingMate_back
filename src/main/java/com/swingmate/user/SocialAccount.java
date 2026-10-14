package com.swingmate.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_accounts")
public class SocialAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    private String email;

    @Column(nullable = false)
    private Instant connectedAt;

    protected SocialAccount() {
    }

    public SocialAccount(UUID userId, SocialProvider provider, String providerUserId, String email) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.connectedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }
}
