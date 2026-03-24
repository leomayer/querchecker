package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.DlCategoryPromptDefinitions.PromptConfig;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhCategoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Befüllt DlCategoryPrompt additiv nach Kategorie-Refresh.
 * Mehrfachaufruf sicher — prüft pro (Kategorie, PromptType) ob bereits ein Eintrag vorhanden.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlCategoryPromptSeeder {

    private final DlCategoryPromptRepository promptRepo;
    private final WhCategoryRepository categoryRepo;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        seedIfAbsent();
    }

    @Transactional
    public void seedIfAbsent() {
        if (categoryRepo.count() == 0) return;

        for (PromptType type : PromptType.values()) {
            // Default-Prompt
            if (promptRepo.findDefaultByPromptType(type).isEmpty()) {
                promptRepo.save(buildDefault(type));
                log.info("Seeded default prompt for {}", type);
            }
            // Kategorie-spezifische Prompts
            DlCategoryPromptDefinitions.CONFIGS.forEach((name, cfgs) ->
                categoryRepo.findByName(name).ifPresent(cat ->
                    cfgs.stream()
                        .filter(cfg -> cfg.promptType() == type)
                        .findFirst()
                        .ifPresent(cfg -> {
                            if (promptRepo.findByWhCategoryAndPromptType(cat, type).isEmpty()) {
                                promptRepo.save(build(cat, cfg));
                                log.info("Seeded {} prompt for category '{}'", type, name);
                            }
                        })
                )
            );
        }

        log.info("DlCategoryPromptSeeder done: {} total", promptRepo.count());
    }

    private DlCategoryPrompt buildDefault(PromptType type) {
        return switch (type) {
            case PRODUCT_NAME -> DlCategoryPrompt.builder()
                .whCategory(null).promptType(type)
                .systemPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_SYSTEM)
                .userPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_USER_DEFAULT)
                .build();
            case QUICK_FACTS -> DlCategoryPrompt.builder()
                .whCategory(null).promptType(type)
                .systemPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_SYSTEM)
                .userPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_USER_DEFAULT)
                .build();
        };
    }

    private DlCategoryPrompt build(WhCategory cat, PromptConfig cfg) {
        return DlCategoryPrompt.builder()
            .whCategory(cat)
            .promptType(cfg.promptType())
            .systemPrompt(cfg.systemPrompt())
            .userPrompt(cfg.userPrompt())
            .build();
    }
}
