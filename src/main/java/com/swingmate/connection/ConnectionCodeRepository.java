package com.swingmate.connection;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionCodeRepository extends JpaRepository<ConnectionCode, UUID> {

    Optional<ConnectionCode> findByCode(String code);

    boolean existsByCode(String code);

    /** 새 코드를 발급하면 그 회원의 이전 코드는 모두 지운다. */
    @Modifying
    @Query("delete from ConnectionCode c where c.memberId = :memberId")
    void deleteByMemberId(UUID memberId);
}
