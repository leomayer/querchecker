package at.querchecker.auth.dto;

import at.querchecker.auth.Role;

import java.time.Instant;

// Response für die Übersicht — KEIN Secret/Hash enthalten
public record AccessKeyOverviewDto(
    Long id,
    Role role,
    int quotaLimit,
    Instant createdAt,
    Instant lastUsedAt,
    boolean revoked
) {}
