package ru.soa.marketplace.security;

import ru.soa.marketplace.domain.Role;

import java.util.UUID;

public record CurrentUser(UUID id, String username, Role role) {
}
