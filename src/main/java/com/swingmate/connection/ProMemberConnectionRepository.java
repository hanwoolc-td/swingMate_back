package com.swingmate.connection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProMemberConnectionRepository extends JpaRepository<ProMemberConnection, UUID> {

    Optional<ProMemberConnection> findByProIdAndMemberId(UUID proId, UUID memberId);

    List<ProMemberConnection> findByMemberIdAndStatusInOrderByRequestedAtDesc(
            UUID memberId, Collection<ConnectionStatus> statuses);

    List<ProMemberConnection> findByProIdAndStatusInOrderByRequestedAtDesc(
            UUID proId, Collection<ConnectionStatus> statuses);
}
