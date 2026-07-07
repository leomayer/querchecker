package at.querchecker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionCleanupScheduler {

    private final UserSessionRepository userSessionRepository;

    @Scheduled(cron = "${querchecker.auth.session-cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredSessions() {
        log.debug("Räume abgelaufene Sessions auf");
        userSessionRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
