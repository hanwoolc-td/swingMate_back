package com.swingmate.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingStatus onboardingStatus;

    private String name;
    private String phoneNumber;
    private Instant phoneVerifiedAt;
    private String profileImageUrl;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User pending() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.onboardingStatus = OnboardingStatus.PENDING;
        return user;
    }

    public boolean isOnboardingCompleted() {
        return onboardingStatus == OnboardingStatus.COMPLETED;
    }

    public void selectRole(Role role) {
        this.role = role;
    }

    public void completeOnboarding(Role role, String name, String phoneNumber, Instant phoneVerifiedAt) {
        this.role = role;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.phoneVerifiedAt = phoneVerifiedAt;
        this.onboardingStatus = OnboardingStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public OnboardingStatus getOnboardingStatus() {
        return onboardingStatus;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
