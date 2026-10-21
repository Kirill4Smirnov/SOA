package ru.soa.marketplace;

import org.junit.jupiter.api.Test;
import ru.soa.marketplace.domain.AppUser;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.security.JwtService;
import ru.soa.marketplace.web.ApiException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private final JwtService jwt = new JwtService(
            "test-secret-test-secret-test-secret-123", Duration.ofMinutes(20), Duration.ofDays(14));
    private final AppUser user = new AppUser(UUID.randomUUID(), "buyer", "hash", Role.USER);

    @Test
    void accessTokenContainsIdentityAndRole() {
        var currentUser = jwt.parseAccess(jwt.accessToken(user));

        assertThat(currentUser.id()).isEqualTo(user.getId());
        assertThat(currentUser.username()).isEqualTo("buyer");
        assertThat(currentUser.role()).isEqualTo(Role.USER);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        assertThatThrownBy(() -> jwt.parseAccess(jwt.refreshToken(user)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo("TOKEN_INVALID"));
    }
}
