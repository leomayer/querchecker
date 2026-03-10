package at.querchecker.wh;

import at.querchecker.repository.WhListingRepository;
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
     * Führt beim Start eine Sofort-Aktualisierung durch, wenn die Tabellen leer sind
     * (z.B. erste Inbetriebnahme oder leere DB).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (whCategoryService.isEmpty() || whLocationService.isEmpty()) {
            log.info("Leere Kategorie- oder Standort-Tabelle erkannt – initialer Abruf gestartet");
            Thread.ofVirtual().start(this::runRefresh);
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
        } finally {
            refreshInProgress.set(false);
        }
    }
}
