package at.querchecker.api.config;

import lombok.Data;

/**
 * Konfiguration für einen einzelnen API-Provider.
 * Wird von ProviderProperties per Provider-Key gemappt.
 * Öffentliche Werte in application.yml, API-Key in secrets.yml.
 */
@Data
public class ProviderConfig {

    /** LLM-Modellname (nur für GROQ, OPENROUTER) */
    private String model;

    /** API-Key — aus secrets.yml, nie in Git */
    private String apiKey = "";

    /** Kostenloses Kontingent (Requests oder Tokens je nach limitUnit) */
    private int freeLimit = 0;

    /** Abrechnungszeitraum */
    private FreeLimitPeriod freeLimitPeriod = FreeLimitPeriod.DAILY;

    /** Tag des Monats, an dem der Monatszeitraum beginnt (1–28) */
    private int periodStartDay = 1;

    /** Einheit des Kontingents */
    private LimitUnit limitUnit = LimitUnit.REQUESTS;

    /** Warnschwelle in Prozent (0–100) */
    private int alertAtPercent = 80;
}
