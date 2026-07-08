package at.querchecker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionCleanupScheduler {

    private final UserSessionRepository userSessionRepository;
    private final AccessKeyUsageRepository accessKeyUsageRepository;
    private final AuthProperties authProperties;

    @Scheduled(cron = "${querchecker.auth.session-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredSessions() {
        log.debug("Räume abgelaufene Sessions auf");
        userSessionRepository.deleteByExpiresAtBefore(Instant.now());

        // DSGVO-Retention der Key-Nutzungshistorie (Konzept Kap. 7)
        LocalDate cutoff = LocalDate.now().minusDays(authProperties.getUsageRetentionDays());
        accessKeyUsageRepository.deleteByPeriodDateBefore(cutoff);
    }
}
