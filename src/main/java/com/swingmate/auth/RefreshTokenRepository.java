package com.swingmate.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** 삭제된 행 수를 반환한다. 0이면 이미 폐기·사용된 토큰이다. */
    @Modifying
    @Query("delete from RefreshToken t where t.id = :id")
    int deleteByIdReturningCount(UUID id);
}
