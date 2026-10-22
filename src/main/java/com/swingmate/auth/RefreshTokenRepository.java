package com.swingmate.auth;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.id = :id")
    Optional<RefreshToken> findByIdForUpdate(@Param("id") UUID id);

    List<RefreshToken> findAllByUserIdOrderByLastUsedAtDesc(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId and t.deviceId = :deviceId")
    int deleteByUserIdAndDeviceId(@Param("userId") UUID userId, @Param("deviceId") UUID deviceId);

    int deleteAllByUserId(UUID userId);

    int deleteByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("delete from RefreshToken t where t.id = :id and t.tokenId = :tokenId")
    int deleteByIdAndTokenIdReturningCount(@Param("id") UUID id, @Param("tokenId") UUID tokenId);
}
