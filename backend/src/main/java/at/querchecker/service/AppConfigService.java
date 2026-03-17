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
