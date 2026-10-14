package com.swingmate.onboarding;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "member_profiles")
public class MemberProfile {

    @Id
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GolfExperience golfExperience;

    @ElementCollection
    @CollectionTable(name = "member_lesson_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest")
    private Set<LessonInterest> lessonInterests = new HashSet<>();

    protected MemberProfile() {
    }

    public MemberProfile(UUID userId, Set<LessonInterest> lessonInterests, GolfExperience golfExperience) {
        this.userId = userId;
        this.lessonInterests = new HashSet<>(lessonInterests);
        this.golfExperience = golfExperience;
    }
}
