package at.querchecker.auth.dto;

import at.querchecker.auth.Role;

public record AuthStatusDto(boolean authenticated, Role role, boolean hasKey, Long accessKeyId) {}
