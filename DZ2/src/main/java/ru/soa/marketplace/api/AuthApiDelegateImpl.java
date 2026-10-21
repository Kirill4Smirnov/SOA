package ru.soa.marketplace.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.soa.marketplace.domain.AppUser;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.generated.api.AuthApiDelegate;
import ru.soa.marketplace.generated.model.LoginRequest;
import ru.soa.marketplace.generated.model.RefreshRequest;
import ru.soa.marketplace.generated.model.RegisterRequest;
import ru.soa.marketplace.generated.model.TokenResponse;
import ru.soa.marketplace.generated.model.UserResponse;
import ru.soa.marketplace.repository.UserRepository;
import ru.soa.marketplace.security.JwtService;
import ru.soa.marketplace.web.ApiException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthApiDelegateImpl(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public ResponseEntity<UserResponse> register(RegisterRequest request) {
        if (users.existsByUsername(request.getUsername())) {
            throw new ApiException(CONFLICT, "USER_ALREADY_EXISTS", "Пользователь с таким именем уже существует");
        }
        AppUser user = new AppUser(
                UUID.randomUUID(),
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                Role.valueOf(request.getRole().getValue()));
        users.save(user);
        return ResponseEntity.status(CREATED).body(new UserResponse(
                user.getId(), user.getUsername(), ru.soa.marketplace.generated.model.Role.fromValue(user.getRole().name())));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<TokenResponse> login(LoginRequest request) {
        AppUser user = users.findByUsername(request.getUsername())
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return ResponseEntity.ok(tokens(user));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<TokenResponse> refresh(RefreshRequest request) {
        var tokenUser = jwtService.parseRefresh(request.getRefreshToken());
        AppUser user = users.findById(tokenUser.id())
                .orElseThrow(() -> new ApiException(UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Пользователь не найден"));
        return ResponseEntity.ok(tokens(user));
    }

    private TokenResponse tokens(AppUser user) {
        return new TokenResponse(
                jwtService.accessToken(user),
                jwtService.refreshToken(user),
                "Bearer",
                jwtService.accessTtlSeconds());
    }

    private ApiException invalidCredentials() {
        return new ApiException(UNAUTHORIZED, "TOKEN_INVALID", "Неверное имя пользователя или пароль");
    }
}
