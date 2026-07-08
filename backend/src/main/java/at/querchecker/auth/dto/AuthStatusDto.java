package at.querchecker.auth.dto;

import at.querchecker.auth.Role;

/**
 * @param quotaRemaining heute verbleibendes Key-Kontingent — nur bei role == USER befüllt, sonst null
 * @param quotaLimit     Tageskontingent des Keys — nur bei role == USER befüllt, sonst null (für "X/Y"-Anzeige)
 */
public record AuthStatusDto(
    boolean authenticated,
    Role role,
    boolean hasKey,
    Long accessKeyId,
    Integer quotaRemaining,
    Integer quotaLimit
) {}
