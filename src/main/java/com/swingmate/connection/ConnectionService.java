package com.swingmate.connection;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.connection.ConnectionDtos.ConnectionCodeResponse;
import com.swingmate.connection.ConnectionDtos.MemberCard;
import com.swingmate.connection.ConnectionDtos.MemberConnectionResponse;
import com.swingmate.connection.ConnectionDtos.ProCard;
import com.swingmate.connection.ConnectionDtos.ProConnectionResponse;
import com.swingmate.onboarding.ProProfile;
import com.swingmate.onboarding.ProProfileRepository;
import com.swingmate.user.Role;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 명세서 CONNECT-01.
 * 회원이 일회용 코드·QR 표시 → 프로가 입력·스캔 → 회원 승인 → 연결 완료.
 */
@Service
public class ConnectionService {

    static final Duration CODE_VALIDITY = Duration.ofMinutes(5);
    /** 헷갈리는 0/O, 1/I 제외 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final List<ConnectionStatus> VISIBLE = List.of(ConnectionStatus.PENDING, ConnectionStatus.ACTIVE);

    private final ConnectionCodeRepository codeRepository;
    private final ProMemberConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final ProProfileRepository proProfileRepository;
    private final SecureRandom random = new SecureRandom();

    public ConnectionService(ConnectionCodeRepository codeRepository,
                             ProMemberConnectionRepository connectionRepository,
                             UserRepository userRepository, ProProfileRepository proProfileRepository) {
        this.codeRepository = codeRepository;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.proProfileRepository = proProfileRepository;
    }

    /** 새 코드를 발급하면 이전 코드는 더 쓸 수 없다. */
    @Transactional
    public ConnectionCodeResponse issueCode(UUID memberId) {
        requireRole(memberId, Role.MEMBER);
        codeRepository.deleteByMemberId(memberId);
        codeRepository.flush();

        Instant now = Instant.now();
        ConnectionCode code = codeRepository.save(
                new ConnectionCode(memberId, newCode(), now, now.plus(CODE_VALIDITY)));
        return new ConnectionCodeResponse(code.getCode(), code.getExpiresAt(), CODE_VALIDITY.toSeconds());
    }

    /** 프로가 회원의 코드를 입력하면 대기 상태의 연결 요청이 생긴다. 코드는 한 번만 쓸 수 있다. */
    @Transactional
    public ProConnectionResponse request(UUID proId, String rawCode) {
        requireRole(proId, Role.PRO);
        Instant now = Instant.now();
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        ConnectionCode code = codeRepository.findByCode(normalized)
                .filter(c -> c.isUsable(now))
                .orElseThrow(() -> new ApiException(ErrorCode.CONNECTION_CODE_INVALID));
        code.markUsed(now);

        ProMemberConnection connection = connectionRepository
                .findByProIdAndMemberId(proId, code.getMemberId())
                .orElse(null);
        if (connection == null) {
            connection = connectionRepository.save(ProMemberConnection.request(proId, code.getMemberId(), now));
        } else if (connection.getStatus() == ConnectionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ALREADY_CONNECTED);
        } else if (connection.getStatus() == ConnectionStatus.ENDED) {
            connection.requestAgain(now);
        }
        User member = findUser(code.getMemberId());
        return toProView(connection, member);
    }

    /** 회원이 받은 연결 요청(대기)과 연결된 프로(연결 중). 최근 요청 순. */
    @Transactional(readOnly = true)
    public List<MemberConnectionResponse> memberConnections(UUID memberId) {
        requireRole(memberId, Role.MEMBER);
        List<ProMemberConnection> connections =
                connectionRepository.findByMemberIdAndStatusInOrderByRequestedAtDesc(memberId, VISIBLE);
        List<UUID> proIds = connections.stream().map(ProMemberConnection::getProId).toList();
        Map<UUID, User> pros = byId(userRepository.findAllById(proIds), User::getId);
        Map<UUID, ProProfile> profiles = byId(proProfileRepository.findAllById(proIds), ProProfile::getUserId);
        return connections.stream()
                .map(c -> new MemberConnectionResponse(c.getId(), c.getStatus(), c.getRequestedAt(),
                        proCard(pros.get(c.getProId()), profiles.get(c.getProId()))))
                .toList();
    }

    /** 프로가 보낸 요청(대기)과 연결된 회원(연결 중). 최근 요청 순. */
    @Transactional(readOnly = true)
    public List<ProConnectionResponse> proConnections(UUID proId) {
        requireRole(proId, Role.PRO);
        List<ProMemberConnection> connections =
                connectionRepository.findByProIdAndStatusInOrderByRequestedAtDesc(proId, VISIBLE);
        Map<UUID, User> members = byId(
                userRepository.findAllById(connections.stream().map(ProMemberConnection::getMemberId).toList()),
                User::getId);
        return connections.stream().map(c -> toProView(c, members.get(c.getMemberId()))).toList();
    }

    @Transactional
    public MemberConnectionResponse approve(UUID memberId, UUID connectionId) {
        return respond(memberId, connectionId, true);
    }

    @Transactional
    public MemberConnectionResponse reject(UUID memberId, UUID connectionId) {
        return respond(memberId, connectionId, false);
    }

    private MemberConnectionResponse respond(UUID memberId, UUID connectionId, boolean approve) {
        requireRole(memberId, Role.MEMBER);
        ProMemberConnection connection = connectionRepository.findById(connectionId)
                .filter(c -> c.getMemberId().equals(memberId))
                .orElseThrow(() -> new ApiException(ErrorCode.CONNECTION_NOT_FOUND));
        if (connection.getStatus() != ConnectionStatus.PENDING) {
            throw new ApiException(ErrorCode.CONNECTION_NOT_PENDING);
        }
        Instant now = Instant.now();
        if (approve) {
            connection.approve(now);
        } else {
            connection.reject(now);
        }
        User pro = findUser(connection.getProId());
        return new MemberConnectionResponse(connection.getId(), connection.getStatus(), connection.getRequestedAt(),
                proCard(pro, proProfileRepository.findById(pro.getId()).orElse(null)));
    }

    private static ProConnectionResponse toProView(ProMemberConnection connection, User member) {
        return new ProConnectionResponse(connection.getId(), connection.getStatus(), connection.getRequestedAt(),
                new MemberCard(member.getId(), member.getName(), member.getProfileImageUrl()));
    }

    private static ProCard proCard(User pro, ProProfile profile) {
        return new ProCard(pro.getId(), pro.getName(), pro.getProfileImageUrl(),
                profile == null ? null : profile.getIntroduction(),
                profile == null ? List.of() : List.copyOf(profile.getCareers()));
    }

    private void requireRole(UUID userId, Role role) {
        if (findUser(userId).getRole() != role) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private String newCode() {
        // 32^6 ≈ 10억 조합, 5분 유효라 추측은 사실상 불가능하다. 충돌하면 다시 만든다.
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            if (!codeRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("연결 코드 생성 실패");
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
