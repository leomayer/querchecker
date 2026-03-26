package at.querchecker.research.seeder;

import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.repository.CategorySearchSourceRepository;
import at.querchecker.research.seeder.CategorySearchSourceDefinitions.SourceConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seedet CategorySearchSource-Einträge je Kategorie.
 * Additive Logik: fehlende Einträge werden ergänzt, bestehende nie überschrieben.
 * Aufruf: nach WhRefreshScheduler (Kategorien müssen in DB sein).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CategorySearchSourceSeeder {

    private final CategorySearchSourceRepository repo;
    private final WhCategoryRepository categoryRepo;

    @Transactional
    public void seedIfAbsent() {
        if (categoryRepo.count() == 0) {
            log.warn("CategorySearchSourceSeeder: wh_category leer — übersprungen");
            return;
        }

        CategorySearchSourceDefinitions.CONFIGS.forEach((categoryName, configs) -> {
            List<WhCategory> matches = categoryRepo.findAllByName(categoryName);
            if (matches.isEmpty()) {
                log.warn("CategorySearchSourceSeeder: Kategorie '{}' nicht in DB — übersprungen", categoryName);
                return;
            }
            for (WhCategory cat : matches) {
                // level=1+2 → inheritFromParent=true, damit Kinder auf level=2 und level=3 erben können
                boolean inheritFromParent = cat.getLevel() <= 2;
                for (int i = 0; i < configs.size(); i++) {
                    SourceConfig cfg = configs.get(i);
                    if (repo.findByWhCategoryAndSiteDomain(cat, cfg.domain()).isEmpty()) {
                        repo.save(CategorySearchSource.builder()
                                .whCategory(cat)
                                .priority(i + 1)
                                .siteDomain(cfg.domain())
                                .siteLabel(cfg.label())
                                .sourceType(cfg.type())
                                .lookupEnabled(cfg.lookupEnabled())
                                .queryExcludes(cfg.queryExcludes())
                                .searchResultCount(cfg.searchResultCount())
                                .inheritFromParent(inheritFromParent)
                                .active(true)
                                .build());
                        log.info("CategorySearchSourceSeeder: '{}' (level={}) → '{}' geseedet",
                                categoryName, cat.getLevel(), cfg.domain());
                    }
                }
            }
        });
    }
}
