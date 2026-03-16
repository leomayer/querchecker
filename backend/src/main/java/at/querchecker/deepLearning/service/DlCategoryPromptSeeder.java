package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.repository.WhCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Befüllt DlCategoryPrompt idempotent nach Kategorie-Refresh.
 * Mehrfachaufruf sicher – bei bereits befüllter Tabelle kein Effekt.
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
        if (promptRepo.count() > 0) return;      // bereits befüllt

        log.info("Seeding DL category prompts...");

        // Default-Prompt
        promptRepo.save(DlCategoryPrompt.builder()
            .whCategory(null)
            .prompt(DlCategoryPromptDefinitions.DEFAULT)
            .build());

        // Kategorie-spezifische Prompts
        var rootCategories = categoryRepo.findByLevelOrderByNameAsc(0);
        DlCategoryPromptDefinitions.CATEGORY_PROMPTS.forEach((name, prompt) ->
            rootCategories.stream()
                .filter(cat -> cat.getName().equals(name))
                .findFirst()
                .ifPresentOrElse(
                    cat -> promptRepo.save(DlCategoryPrompt.builder()
                        .whCategory(cat)
                        .prompt(prompt)
                        .build()),
                    () -> log.warn("Category '{}' not found in wh_category — skipping prompt", name)
                )
        );

        log.info("Seeded {} DL category prompts (incl. default)", promptRepo.count());
    }
}
