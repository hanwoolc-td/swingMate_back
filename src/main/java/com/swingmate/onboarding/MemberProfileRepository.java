package com.swingmate.onboarding;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, UUID> {
}
