package at.querchecker.service;

import at.querchecker.entity.AppConfig;
import at.querchecker.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    static final String DL_CONTEXT_MAX_TOKENS_KEY = "dl.context.max_tokens";
    static final int DL_CONTEXT_MAX_TOKENS_DEFAULT = 512;

    static final String LOOKUP_FAILED_TTL_HOURS_KEY = "product.lookup.failed.ttl.hours";
    static final int LOOKUP_FAILED_TTL_HOURS_DEFAULT = 24;

    static final String LOOKUP_ERROR_TTL_MINUTES_KEY = "product.lookup.error.ttl.minutes";
    static final int LOOKUP_ERROR_TTL_MINUTES_DEFAULT = 10;

    private final AppConfigRepository appConfigRepo;

    public int getDlContextMaxTokens() {
        return appConfigRepo.findById(DL_CONTEXT_MAX_TOKENS_KEY)
            .map(c -> {
                try {
                    return Integer.parseInt(c.getValue());
                } catch (NumberFormatException e) {
                    return DL_CONTEXT_MAX_TOKENS_DEFAULT;
                }
            })
            .orElse(DL_CONTEXT_MAX_TOKENS_DEFAULT);
    }

    public int getLookupFailedTtlHours() {
        return appConfigRepo.findById(LOOKUP_FAILED_TTL_HOURS_KEY)
            .map(c -> {
                try { return Integer.parseInt(c.getValue()); }
                catch (NumberFormatException e) { return LOOKUP_FAILED_TTL_HOURS_DEFAULT; }
            })
            .orElse(LOOKUP_FAILED_TTL_HOURS_DEFAULT);
    }

    public int getLookupErrorTtlMinutes() {
        return appConfigRepo.findById(LOOKUP_ERROR_TTL_MINUTES_KEY)
            .map(c -> {
                try { return Integer.parseInt(c.getValue()); }
                catch (NumberFormatException e) { return LOOKUP_ERROR_TTL_MINUTES_DEFAULT; }
            })
            .orElse(LOOKUP_ERROR_TTL_MINUTES_DEFAULT);
    }

    public void setDlContextMaxTokens(int maxTokens) {
        LocalDateTime now = LocalDateTime.now();
        appConfigRepo.save(AppConfig.builder()
            .key(DL_CONTEXT_MAX_TOKENS_KEY)
            .value(String.valueOf(maxTokens))
            .description("Maximale Tokenanzahl für den Eingabe-Kontext aller AI-Extraktionsmodelle")
            .updatedAt(now)
            .build());
    }
}
