package ru.soa.marketplace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.soa.marketplace.domain.AppUser;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.web.ApiException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-ttl}") Duration accessTtl,
                      @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String accessToken(AppUser user) { return create(user, "access", accessTtl); }
    public String refreshToken(AppUser user) { return create(user, "refresh", refreshTtl); }
    public long accessTtlSeconds() { return accessTtl.toSeconds(); }

    public CurrentUser parseAccess(String token) {
        Claims claims = parse(token, "TOKEN_EXPIRED", "TOKEN_INVALID");
        if (!"access".equals(claims.get("type", String.class))) {
            throw new ApiException(UNAUTHORIZED, "TOKEN_INVALID", "Ожидался access token");
        }
        return toCurrentUser(claims, "TOKEN_INVALID");
    }

    public CurrentUser parseRefresh(String token) {
        Claims claims = parse(token, "REFRESH_TOKEN_INVALID", "REFRESH_TOKEN_INVALID");
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ApiException(UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Ожидался refresh token");
        }
        return toCurrentUser(claims, "REFRESH_TOKEN_INVALID");
    }

    private String create(AppUser user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, String expiredCode, String invalidCode) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            throw new ApiException(UNAUTHORIZED, expiredCode, "Срок действия токена истёк");
        } catch (Exception exception) {
            throw new ApiException(UNAUTHORIZED, invalidCode, "Токен недействителен");
        }
    }

    private CurrentUser toCurrentUser(Claims claims, String invalidCode) {
        try {
            return new CurrentUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (Exception exception) {
            throw new ApiException(UNAUTHORIZED, invalidCode, "Токен содержит неверные данные");
        }
    }
}
