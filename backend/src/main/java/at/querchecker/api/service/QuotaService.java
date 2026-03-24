package at.querchecker.api.service;

import at.querchecker.api.config.FreeLimitPeriod;
import at.querchecker.api.config.LimitUnit;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Prüft und verwaltet API-Kontingente basierend auf ProviderProperties.
 * Unterstützt DAILY/MONTHLY Perioden und REQUESTS/TOKENS Einheiten.
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final ApiUsageLogService usageLogService;
    private final ProviderProperties providerProperties;

    /** Prüft ob das Kontingent für diesen Provider noch frei ist. */
    public QuotaStatus checkQuota(Provider provider) {
        if (provider == Provider.ICECAT) return QuotaStatus.OK; // kein Kontingent
        ProviderConfig config = providerProperties.getProvider(provider);
        long usage = currentUsage(provider, config);
        return usage >= config.getFreeLimit() ? QuotaStatus.QUOTA_EXCEEDED : QuotaStatus.OK;
    }

    /** Prüft ob die Warnschwelle (alert-at-percent) erreicht oder überschritten ist. */
    public boolean isWarningThreshold(Provider provider) {
        if (provider == Provider.ICECAT) return false;
        ProviderConfig config = providerProperties.getProvider(provider);
        long usage = currentUsage(provider, config);
        long threshold = (long) (config.getFreeLimit() * (config.getAlertAtPercent() / 100.0));
        return usage >= threshold;
    }

    /** Berechnet den Beginn der aktuellen Abrechnungsperiode (verwendet heutiges Datum). */
    public LocalDate getPeriodStart(FreeLimitPeriod period, int periodStartDay) {
        return getPeriodStart(period, periodStartDay, LocalDate.now());
    }

    /** Berechnet den Beginn der aktuellen Abrechnungsperiode für ein gegebenes Datum. */
    public LocalDate getPeriodStart(FreeLimitPeriod period, int periodStartDay, LocalDate today) {
        if (period == FreeLimitPeriod.DAILY) {
            return today;
        }
        // MONTHLY: Periode startet am periodStartDay des Monats
        LocalDate candidateThisMonth = today.withDayOfMonth(periodStartDay);
        if (!today.isBefore(candidateThisMonth)) {
            return candidateThisMonth;
        }
        // Heute ist vor dem Starttag → Periode begann im Vormonat
        return candidateThisMonth.minusMonths(1);
    }

    /** Nächstes Reset-Datum (Ende der aktuellen Periode + 1 Tag). */
    public LocalDate getQuotaResetDate(Provider provider) {
        ProviderConfig config = providerProperties.getProvider(provider);
        LocalDate periodStart = getPeriodStart(config.getFreeLimitPeriod(), config.getPeriodStartDay());
        if (config.getFreeLimitPeriod() == FreeLimitPeriod.DAILY) {
            return periodStart.plusDays(1);
        }
        return periodStart.plusMonths(1);
    }

    // --- private helpers ---

    private long currentUsage(Provider provider, ProviderConfig config) {
        LocalDateTime from = getPeriodStart(
            config.getFreeLimitPeriod(), config.getPeriodStartDay()
        ).atStartOfDay();
        LocalDateTime to = LocalDateTime.now();

        if (config.getLimitUnit() == LimitUnit.TOKENS) {
            return usageLogService.sumTokensByProviderAndPeriod(provider, from, to);
        }
        return usageLogService.countByProviderAndPeriod(provider, from, to);
    }
}
