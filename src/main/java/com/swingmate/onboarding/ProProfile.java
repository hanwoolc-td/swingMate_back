package com.swingmate.onboarding;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pro_profiles")
public class ProProfile {

    @Id
    private UUID userId;

    @Column(nullable = false)
    private String introduction;

    @ElementCollection
    @CollectionTable(name = "pro_careers", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "career")
    private List<String> careers = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "pro_availability", joinColumns = @JoinColumn(name = "user_id"))
    private List<AvailabilitySlot> availability = new ArrayList<>();

    protected ProProfile() {
    }

    public ProProfile(UUID userId, String introduction, List<String> careers, List<AvailabilitySlot> availability) {
        this.userId = userId;
        this.introduction = introduction;
        this.careers = new ArrayList<>(careers);
        this.availability = new ArrayList<>(availability);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIntroduction() {
        return introduction;
    }

    public List<String> getCareers() {
        return careers;
    }

    @Embeddable
    public static class AvailabilitySlot {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private DayOfWeek dayOfWeek;

        @Column(nullable = false)
        private LocalTime startTime;

        @Column(nullable = false)
        private LocalTime endTime;

        protected AvailabilitySlot() {
        }

        public AvailabilitySlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
