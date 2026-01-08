package tw.bk.appauth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import tw.bk.appauth.config.AuthProperties;
import tw.bk.apppersistence.entity.UserEntity;

@Component
public class JwtTokenService {
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final AuthProperties properties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        this.secretKey = buildSecretKey(properties.getJwtSecret());
    }

    public String buildAccessToken(UserEntity user, String jti, Duration ttl) {
        return buildToken(user.getId(), user.getEmail(), jti, TYPE_ACCESS, ttl);
    }

    public String buildRefreshToken(UserEntity user, String jti, Duration ttl) {
        return buildToken(user.getId(), user.getEmail(), jti, TYPE_REFRESH, ttl);
    }

    public String buildAccessToken(Long userId, String email, String jti, Duration ttl) {
        return buildToken(userId, email, jti, TYPE_ACCESS, ttl);
    }

    public String buildRefreshToken(Long userId, String email, String jti, Duration ttl) {
        return buildToken(userId, email, jti, TYPE_REFRESH, ttl);
    }

    public TokenClaims parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        String type = claims.get(CLAIM_TYPE, String.class);
        String jti = claims.getId();
        return new TokenClaims(userId, email, type, jti);
    }

    private String buildToken(Long userId, String email, String jti, String type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(jti)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private SecretKey buildSecretKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret is required");
        }
        byte[] keyBytes;
        if (raw.matches("^[A-Za-z0-9+/=]+$") && raw.length() % 4 == 0) {
            keyBytes = Decoders.BASE64.decode(raw);
        } else {
            keyBytes = raw.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record TokenClaims(Long userId, String email, String type, String jti) {
    }
}
