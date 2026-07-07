package at.querchecker.auth.dto;

import at.querchecker.auth.Role;

import java.time.Instant;

// Response bei Generierung — Klartext-Key NUR hier enthalten, nie wieder abrufbar
public record AccessKeyCreatedDto(
    Long id,
    String secretKey, // Klartext, einmalig
    Role role,
    int quotaLimit,
    Instant createdAt
) {}
