package at.querchecker.willHaben;

import at.querchecker.deepLearning.service.DlCategoryPromptSeeder;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.research.seeder.CategorySearchSourceSeeder;
import at.querchecker.research.seeder.CategorySpecPreferenceSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhRefreshScheduler {

    private final WhCategoryService whCategoryService;
    private final WhLocationService whLocationService;
    private final WhListingRepository whListingRepository;
    private final DlCategoryPromptSeeder dlCategoryPromptSeeder;
    private final CategorySpecPreferenceSeeder categorySpecPreferenceSeeder;
    private final CategorySearchSourceSeeder categorySearchSourceSeeder;

    @Value("${querchecker.wh.refresh.cron:0 0 3 * * MON}")
    private String refreshCron;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    public String getRefreshCron() {
        return refreshCron;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupStaleListings() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        log.info("Cleanup veralteter Listings (älter als {})", cutoff);
        whListingRepository.deleteStaleListings(cutoff);
    }

    /** Läuft nach dem konfigurierten Cron-Ausdruck (Standard: montags um 03:00). */
    @Scheduled(cron = "${querchecker.wh.refresh.cron:0 0 3 * * MON}")
    public void scheduledRefresh() {
        log.info("Geplante Willhaben-Aktualisierung gestartet");
        runRefresh();
    }

    /**
     * Beim Start:
     * - Tabellen leer → vollständiger Refresh (Willhaben-Fetch + Seeder)
     * - Tabellen befüllt → nur Seeder (additive Logik, idempotent)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (whCategoryService.isEmpty() || whLocationService.isEmpty()) {
            log.info("Leere Kategorie- oder Standort-Tabelle erkannt – initialer Abruf gestartet");
            Thread.ofVirtual().start(this::runRefresh);
        } else {
            log.info("Kategorien vorhanden – nur Seeder ausführen");
            dlCategoryPromptSeeder.seedIfAbsent();
            categorySpecPreferenceSeeder.seedIfAbsent();
            categorySearchSourceSeeder.seedIfAbsent();
        }
    }

    /**
     * Startet einen Refresh-Durchlauf. Läuft er bereits, wird der Aufruf ignoriert.
     * Kann direkt (synchron) oder über einen virtuellen Thread (asynchron) aufgerufen werden.
     */
    public void runRefresh() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("Aktualisierung läuft bereits, überspringe");
            return;
        }
        try {
            whCategoryService.fetchAndUpsert();
            whLocationService.fetchAndUpsert();
            dlCategoryPromptSeeder.seedIfAbsent();
            categorySpecPreferenceSeeder.seedIfAbsent();
            categorySearchSourceSeeder.seedIfAbsent();
        } finally {
            refreshInProgress.set(false);
        }
    }
}
