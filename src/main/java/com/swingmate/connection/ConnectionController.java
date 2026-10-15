package com.swingmate.connection;

import com.swingmate.connection.ConnectionDtos.ConnectionCodeResponse;
import com.swingmate.connection.ConnectionDtos.ConnectionRequest;
import com.swingmate.connection.ConnectionDtos.MemberConnectionResponse;
import com.swingmate.connection.ConnectionDtos.ProConnectionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 가입 완료 사용자의 액세스 토큰으로 호출한다. 회원용·프로용 API는 역할이 다르면 403. */
@RestController
public class ConnectionController {

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/members/me/connection-code")
    public ConnectionCodeResponse issueCode(@AuthenticationPrincipal UUID memberId) {
        return service.issueCode(memberId);
    }

    @GetMapping("/api/v1/members/me/connections")
    public List<MemberConnectionResponse> memberConnections(@AuthenticationPrincipal UUID memberId) {
        return service.memberConnections(memberId);
    }

    @PostMapping("/api/v1/members/me/connections/{connectionId}/approve")
    public MemberConnectionResponse approve(@AuthenticationPrincipal UUID memberId,
                                            @PathVariable UUID connectionId) {
        return service.approve(memberId, connectionId);
    }

    @PostMapping("/api/v1/members/me/connections/{connectionId}/reject")
    public MemberConnectionResponse reject(@AuthenticationPrincipal UUID memberId,
                                           @PathVariable UUID connectionId) {
        return service.reject(memberId, connectionId);
    }

    @PostMapping("/api/v1/pros/me/connections")
    public ProConnectionResponse request(@AuthenticationPrincipal UUID proId,
                                         @RequestBody ConnectionRequest request) {
        return service.request(proId, request.code());
    }

    @GetMapping("/api/v1/pros/me/connections")
    public List<ProConnectionResponse> proConnections(@AuthenticationPrincipal UUID proId) {
        return service.proConnections(proId);
    }
}
