package at.querchecker.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ebene-2-Kontingent (Key-Kontingent) aus dem Berechtigungskonzept, Kap. 4.
 *
 * <p>Alle Methoden behandeln {@code accessKeyId == null} als „kein Kontingent" —
 * das gilt für SUPERUSER (auch dev/local-profile) und GUEST. Die Skip-Regel liegt
 * damit bewusst hier, nicht beim Aufrufer: dieser übergibt bei SUPERUSER schlicht
 * {@code null}.
 */
@Service
@RequiredArgsConstructor
public class AccessKeyUsageService {

    /** Ebene-2b: DL-Extraktion darf ein Vielfaches des Lookup-Kontingents verbrauchen —
     * automatisch pro Detailansicht ausgelöst, nicht explizit angefragt (Konzept Kap. 4). */
    private static final int EXTRACTION_QUOTA_MULTIPLIER = 5;

    private final AccessKeyUsageRepository repo;

    /**
     * Prüft das Tageskontingent vor der Provider-Pipeline.
     *
     * @throws QuotaExceededException wenn kein Kontingent mehr übrig ist
     */
    public void checkQuota(Long accessKeyId) {
        if (accessKeyId == null) return; // SUPERUSER/GUEST → kein Check
        Integer remaining = repo.findRemainingToday(accessKeyId);
        if (remaining != null && remaining <= 0) {
            throw new QuotaExceededException(accessKeyId);
        }
    }

    /**
     * Bucht eine Nutzeraktion — nur nach erfolgreichem Abschluss aufrufen
     * (Cache-Hit und Provider-Erschöpfung zählen nicht, Konzept Kap. 4).
     */
    @Transactional
    public void consume(Long accessKeyId) {
        if (accessKeyId == null) return; // SUPERUSER/GUEST → kein Verbrauch
        repo.incrementToday(accessKeyId);
    }

    /**
     * Verbleibendes Tageskontingent für die Anzeige (GET /api/auth/me).
     * {@code null} bei fehlendem Key.
     */
    public Integer remainingToday(Long accessKeyId) {
        if (accessKeyId == null) return null;
        return repo.findRemainingToday(accessKeyId);
    }

    /**
     * Prüft das Hintergrund-Tageskontingent für DL-Extraktion (Ebene-2b) — gibt {@code true}
     * zurück, wenn noch Kontingent übrig ist. {@code true} für SUPERUSER/GUEST ({@code null}).
     * Kein Exception-Wurf wie bei {@link #checkQuota}: Aufrufer behandelt eine Ablehnung als
     * stillen No-op (CANCELLED-Retry via bestehenden Mechanismus), nicht als Fehler.
     */
    public boolean checkExtractionQuota(Long accessKeyId) {
        if (accessKeyId == null) return true;
        Integer remaining = repo.findExtractionRemainingToday(accessKeyId, EXTRACTION_QUOTA_MULTIPLIER);
        return remaining == null || remaining > 0;
    }

    // REQUIRES_NEW statt REQUIRED: wird aus WhItemService.openDetail()'s
    // TransactionSynchronization.afterCommit() aufgerufen — zu diesem Zeitpunkt ist die
    // umgebende Transaktion bereits committed, aber die Synchronisations-Verarbeitung des
    // Threads läuft noch. Ein REQUIRED-Aufruf hängt sich dann an die bereits beendete
    // Transaktion an statt eine neue zu öffnen → TransactionRequiredException beim
    // executeUpdate() der nativen Modifying-Query. REQUIRES_NEW erzwingt eine frische,
    // unabhängige Transaktion und umgeht das.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeExtraction(Long accessKeyId) {
        if (accessKeyId == null) return;
        repo.incrementExtractionToday(accessKeyId);
    }
}
