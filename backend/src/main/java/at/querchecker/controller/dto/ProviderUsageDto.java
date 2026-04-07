package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProviderUsageDto {
    private long calls;
    private long tokensIn;
    private long tokensOut;
    private long quotaUsage;
    private long quotaLimit;
    /** Modellname — nur bei LLM-Providern (Groq, OpenRouter), sonst null */
    private String model;
    /** Abrechnungszeitraum — DAILY | MONTHLY, null wenn kein Kontingent */
    private String quotaPeriod;
    /** Anzahl Rate-Limit-Hits (HTTP 429) im aktuellen Zeitraum */
    private long rateLimitCount;
    /** Geschätzte Input-Tokens aus rate-limiteten Requests (kann 0 sein bei fehlendem Wert) */
    private long rateLimitEstimatedTokens;
}
