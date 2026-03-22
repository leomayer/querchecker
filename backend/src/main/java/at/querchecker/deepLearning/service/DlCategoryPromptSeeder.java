package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.repository.WhCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Befüllt DlCategoryPrompt idempotent nach Kategorie-Refresh.
 * Mehrfachaufruf sicher — prüft pro PromptType ob bereits Einträge vorhanden.
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

    public void seedIfAbsent() {
        if (categoryRepo.count() == 0) return;  // Kategorien noch nicht geladen

        boolean needsProductName = promptRepo.countByPromptType(PromptType.PRODUCT_NAME) == 0;
        boolean needsQuickFacts  = promptRepo.countByPromptType(PromptType.QUICK_FACTS)  == 0;

        if (!needsProductName && !needsQuickFacts) return;  // bereits vollständig befüllt

        if (needsProductName) {
            log.info("Seeding PRODUCT_NAME prompts...");
            seedProductNamePrompts();
        }
        if (needsQuickFacts) {
            log.info("Seeding QUICK_FACTS prompts...");
            seedQuickFactsPrompts();
        }

        log.info("Seeded DL category prompts: {} total", promptRepo.count());
    }

    private void seedProductNamePrompts() {
        // Default-Prompt
        promptRepo.save(DlCategoryPrompt.builder()
            .whCategory(null)
            .promptType(PromptType.PRODUCT_NAME)
            .systemPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_SYSTEM)
            .userPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_USER_DEFAULT)
            .build());

        // Kategorie-spezifische Prompts (Level-0-Kategorien)
        var rootCategories = categoryRepo.findByLevelOrderByNameAsc(0);
        DlCategoryPromptDefinitions.PRODUCT_NAME_USER_BY_CATEGORY.forEach((name, userPrompt) ->
            rootCategories.stream()
                .filter(cat -> cat.getName().equals(name))
                .findFirst()
                .ifPresentOrElse(
                    cat -> promptRepo.save(DlCategoryPrompt.builder()
                        .whCategory(cat)
                        .promptType(PromptType.PRODUCT_NAME)
                        .systemPrompt(DlCategoryPromptDefinitions.PRODUCT_NAME_SYSTEM)
                        .userPrompt(userPrompt)
                        .build()),
                    () -> log.warn("Category '{}' not found in wh_category — skipping PRODUCT_NAME prompt", name)
                )
        );
    }

    private void seedQuickFactsPrompts() {
        // Default-Prompt
        promptRepo.save(DlCategoryPrompt.builder()
            .whCategory(null)
            .promptType(PromptType.QUICK_FACTS)
            .systemPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_SYSTEM)
            .userPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_USER_DEFAULT)
            .build());

        // Kategorie-spezifische Prompts (beliebige Ebene — Subkategorien wie "Laptop / Notebook")
        DlCategoryPromptDefinitions.QUICK_FACTS_USER_BY_CATEGORY.forEach((name, userPrompt) ->
            categoryRepo.findByName(name)
                .ifPresentOrElse(
                    cat -> promptRepo.save(DlCategoryPrompt.builder()
                        .whCategory(cat)
                        .promptType(PromptType.QUICK_FACTS)
                        .systemPrompt(DlCategoryPromptDefinitions.QUICK_FACTS_SYSTEM)
                        .userPrompt(userPrompt)
                        .build()),
                    () -> log.warn("Category '{}' not found in wh_category — skipping QUICK_FACTS prompt", name)
                )
        );
    }
}
