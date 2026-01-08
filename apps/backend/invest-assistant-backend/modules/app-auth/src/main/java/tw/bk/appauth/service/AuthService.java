package tw.bk.appauth.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.appauth.jwt.JwtTokenService;
import tw.bk.appauth.redis.RefreshTokenStore;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.UserEntity;

@Service
public class AuthService {
    private final JwtTokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthProperties properties;

    public AuthService(JwtTokenService tokenService, RefreshTokenStore refreshTokenStore, AuthProperties properties) {
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.properties = properties;
    }

    public AuthTokens issueTokens(UserEntity user) {
        String refreshJti = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String accessToken = tokenService.buildAccessToken(user, accessJti, properties.getAccessTokenTtl());
        String refreshToken = tokenService.buildRefreshToken(user, refreshJti, properties.getRefreshTokenTtl());
        refreshTokenStore.store(refreshJti, user.getId(), properties.getRefreshTokenTtl());
        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens refreshTokens(String refreshToken) {
        JwtTokenService.TokenClaims claims;
        try {
            claims = tokenService.parseToken(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid refresh token");
        }
        if (!"refresh".equals(claims.type())) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid refresh token");
        }
        Long storedUserId = refreshTokenStore.findUserId(claims.jti()).orElseThrow(() ->
                new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Refresh token revoked"));
        if (!storedUserId.equals(claims.userId())) {
            refreshTokenStore.revoke(claims.jti());
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Refresh token mismatch");
        }

        refreshTokenStore.revoke(claims.jti());
        String refreshJti = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String accessToken = tokenService.buildAccessToken(claims.userId(), claims.email(), accessJti,
                properties.getAccessTokenTtl());
        String newRefreshToken = tokenService.buildRefreshToken(claims.userId(), claims.email(), refreshJti,
                properties.getRefreshTokenTtl());
        refreshTokenStore.store(refreshJti, claims.userId(), properties.getRefreshTokenTtl());
        return new AuthTokens(accessToken, newRefreshToken);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            JwtTokenService.TokenClaims claims = tokenService.parseToken(refreshToken);
            refreshTokenStore.revoke(claims.jti());
        } catch (Exception ignored) {
            // ignore invalid token
        }
    }

    public Duration accessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.getRefreshTokenTtl();
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
