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
 * Befüllt DlCategoryPrompt per Upsert beim Start.
 * Bestehende Einträge werden mit dem aktuellen Stand aus DlCategoryPromptDefinitions überschrieben,
 * sodass Prompt-Änderungen in Java bei jedem Neustart wirksam werden.
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
            upsertDefault(type);
            // Kategorie-spezifische Prompts
            DlCategoryPromptDefinitions.CONFIGS.forEach((name, cfgs) ->
                categoryRepo.findByName(name).ifPresent(cat ->
                    cfgs.stream()
                        .filter(cfg -> cfg.promptType() == type)
                        .findFirst()
                        .ifPresent(cfg -> upsertCategory(cat, cfg, type, name))
                )
            );
        }

        log.info("DlCategoryPromptSeeder done: {} total", promptRepo.count());
    }

    private void upsertDefault(PromptType type) {
        DlCategoryPrompt prompt = promptRepo.findDefaultByPromptType(type)
                .orElseGet(() -> buildDefault(type));
        applyDefault(prompt, type);
        promptRepo.save(prompt);
        log.debug("Upserted default prompt for {}", type);
    }

    private void upsertCategory(WhCategory cat, PromptConfig cfg, PromptType type, String name) {
        DlCategoryPrompt prompt = promptRepo.findByWhCategoryAndPromptType(cat, type)
                .orElseGet(() -> build(cat, cfg));
        prompt.setSystemPrompt(cfg.systemPrompt());
        prompt.setUserPrompt(cfg.userPrompt());
        promptRepo.save(prompt);
        log.debug("Upserted {} prompt for category '{}'", type, name);
    }

    private void applyDefault(DlCategoryPrompt prompt, PromptType type) {
        switch (type) {
            case PRODUCT_NAME -> {
                prompt.setSystemPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_SYSTEM);
                prompt.setUserPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_USER_DEFAULT);
            }
            case QUICK_FACTS -> {
                prompt.setSystemPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_SYSTEM);
                prompt.setUserPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_USER_DEFAULT);
            }
            case HTML_FULL_SPECS -> {
                prompt.setSystemPrompt(DlCategoryPromptDefinitions.HTML_FULL_SPECS_SYSTEM);
                prompt.setUserPrompt(DlCategoryPromptDefinitions.HTML_FULL_SPECS_USER_DEFAULT);
            }
        }
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
            case HTML_FULL_SPECS -> DlCategoryPrompt.builder()
                .whCategory(null).promptType(type)
                .systemPrompt(DlCategoryPromptDefinitions.HTML_FULL_SPECS_SYSTEM)
                .userPrompt(DlCategoryPromptDefinitions.HTML_FULL_SPECS_USER_DEFAULT)
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
