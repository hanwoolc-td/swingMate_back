package com.swingmate.auth;

import com.swingmate.auth.AuthDtos.SocialLoginResponse;
import com.swingmate.auth.AuthDtos.TokenResponse;
import com.swingmate.auth.social.SocialTokenVerifier;
import com.swingmate.auth.social.SocialUserInfo;
import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.SocialAccount;
import com.swingmate.user.SocialAccountRepository;
import com.swingmate.user.SocialProvider;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import com.swingmate.user.UserResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SocialTokenVerifier socialTokenVerifier;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;

    public AuthService(SocialTokenVerifier socialTokenVerifier, UserRepository userRepository,
                       SocialAccountRepository socialAccountRepository,
                       RefreshTokenRepository refreshTokenRepository, JwtTokenProvider tokenProvider) {
        this.socialTokenVerifier = socialTokenVerifier;
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 가입 완료 사용자는 액세스·리프레시 토큰을, 신규 또는 가입 미완료 사용자는 온보딩 토큰을 받는다.
     */
    @Transactional
    public SocialLoginResponse socialLogin(SocialProvider provider, String token) {
        SocialUserInfo info = socialTokenVerifier.verify(provider, token);

        var existing = socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
        boolean isNewUser = existing.isEmpty();
        // ponytail: 같은 계정의 동시 첫 로그인은 유니크 제약으로 한쪽이 실패한다. 앱이 재시도하면 기존 사용자로 로그인된다.
        User user = existing
                .map(account -> userRepository.findById(account.getUserId())
                        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND)))
                .orElseGet(() -> register(provider, info));

        if (!user.isOnboardingCompleted()) {
            return new SocialLoginResponse(isNewUser, user.getOnboardingStatus(), null, null,
                    tokenProvider.createOnboardingToken(user.getId()), null);
        }
        TokenResponse tokens = issueTokens(user.getId());
        return new SocialLoginResponse(isNewUser, user.getOnboardingStatus(), tokens.accessToken(),
                tokens.refreshToken(), null, UserResponse.from(user));
    }

    /** 리프레시 토큰을 회전한다. 한 번 사용한 리프레시 토큰은 다시 쓸 수 없다. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        var parsed = tokenProvider.parse(refreshToken, TokenType.REFRESH)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (refreshTokenRepository.deleteByIdReturningCount(parsed.tokenId()) == 0) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return issueTokens(parsed.userId());
    }

    /** 이미 폐기됐거나 형식이 잘못된 토큰도 성공으로 처리한다(멱등). */
    @Transactional
    public void logout(String refreshToken) {
        tokenProvider.parse(refreshToken, TokenType.REFRESH)
                .ifPresent(parsed -> refreshTokenRepository.deleteByIdReturningCount(parsed.tokenId()));
    }

    private User register(SocialProvider provider, SocialUserInfo info) {
        User user = userRepository.save(User.pending());
        socialAccountRepository.save(new SocialAccount(user.getId(), provider, info.providerUserId(), info.email()));
        return user;
    }

    public TokenResponse issueTokens(UUID userId) {
        UUID tokenId = UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(tokenId, userId, tokenProvider.refreshTokenExpiresAt()));
        return new TokenResponse(tokenProvider.createAccessToken(userId),
                tokenProvider.createRefreshToken(userId, tokenId));
    }
}
