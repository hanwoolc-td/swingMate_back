package com.swingmate.phone;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

    Optional<PhoneVerification> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant since);
}
