package com.swingmate.connection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConnectionDtos {

    private ConnectionDtos() {
    }

    /** 앱은 code를 그대로 화면에 표시하고 QR로도 만든다. */
    public record ConnectionCodeResponse(String code, Instant expiresAt, long remainingSeconds) {
    }

    public record ConnectionRequest(String code) {
    }

    /** 회원이 승인 전에 확인하는 프로 정보. */
    public record ProCard(UUID id, String name, String profileImageUrl, String introduction, List<String> careers) {
    }

    /** 승인 전에는 프로에게 회원 이름만 보인다. */
    public record MemberCard(UUID id, String name, String profileImageUrl) {
    }

    public record MemberConnectionResponse(UUID id, ConnectionStatus status, Instant requestedAt, ProCard pro) {
    }

    public record ProConnectionResponse(UUID id, ConnectionStatus status, Instant requestedAt, MemberCard member) {
    }
}
