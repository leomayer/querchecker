package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.config.DlConfig;
import at.querchecker.deepLearning.repository.ItemTextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Löscht alte ItemText-Records die:
 * - nicht der neueste Record für ihr WhListing sind
 * - älter als konfigurierte retention-days sind
 * - keine userCorrectedTerm in ihren DlExtractionTerms haben (Trainingsdaten schützen)
 * DlExtractionRun + DlExtractionTerm kaskadieren via ON DELETE CASCADE automatisch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemTextCleanupScheduler {

    private final ItemTextRepository repo;
    private final DlConfig dlConfig;

    @Scheduled(cron = "${querchecker.dl.item-text-cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(dlConfig.getItemTextCleanup().getRetentionDays());
        log.info("ItemText cleanup: deleting records older than {}", cutoff);
        repo.deleteOutdatedOlderThan(cutoff);
    }
}
