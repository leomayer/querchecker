package at.querchecker.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    void deleteByAccessKeyId(Long accessKeyId);
    void deleteByExpiresAtBefore(Instant instant);
}
