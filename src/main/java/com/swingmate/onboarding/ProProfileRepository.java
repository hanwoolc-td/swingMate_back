package com.swingmate.onboarding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProProfileRepository extends JpaRepository<ProProfile, UUID> {

    Optional<ProProfile> findByProCode(String proCode);

    boolean existsByProCode(String proCode);
}
