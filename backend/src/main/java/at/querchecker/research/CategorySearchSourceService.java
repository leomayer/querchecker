package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.repository.CategorySearchSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySearchSourceService {

    private final CategorySearchSourceRepository repo;

    /**
     * Liefert aktive, lookup-enabled Quellen für eine Kategorie.
     * Suchreihenfolge:
     * 1. Eigene Einträge mit lookupEnabled=true
     * 2. Eigener Eintrag mit lookupEnabled=false → leere Liste (kein Lookup)
     * 3. Parent-Einträge mit inheritFromParent=true (rekursiv nach oben)
     * 4. Nichts → leere Liste
     */
    public List<CategorySearchSource> findForCategory(WhCategory category) {
        if (category == null) return List.of();

        log.debug("[CategorySearchSourceService] Searching sources for category: id={}, name={}, level={}",
                category.getId(), category.getName(), category.getLevel());

        List<CategorySearchSource> own =
                repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(category);

        if (!own.isEmpty()) {
            log.debug("[CategorySearchSourceService] Found {} own sources for category {}", own.size(), category.getName());
            // lookupEnabled=false → kein Lookup für diese Kategorie
            return own.stream()
                    .filter(CategorySearchSource::isLookupEnabled)
                    .toList();
        }

        log.debug("[CategorySearchSourceService] No own sources found, checking parent hierarchy...");

        // Rekursiv nach oben: Parent mit inheritFromParent=true suchen
        WhCategory current = category.getParent();
        int level = 1;
        while (current != null) {
            log.debug("[CategorySearchSourceService] Checking parent level {}: id={}, name={}",
                    level, current.getId(), current.getName());
            List<CategorySearchSource> parentSources =
                    repo.findByWhCategoryAndInheritFromParentTrueAndActiveTrueOrderByPriorityAsc(current);
            log.debug("[CategorySearchSourceService]   Found {} sources with inheritFromParent=true", parentSources.size());

            List<CategorySearchSource> enabled = parentSources.stream()
                    .filter(CategorySearchSource::isLookupEnabled)
                    .toList();
            if (!enabled.isEmpty()) {
                log.debug("[CategorySearchSourceService] SUCCESS: Using {} inherited sources from parent {}", enabled.size(), current.getName());
                return enabled;
            }
            current = current.getParent();
            level++;
        }

        log.warn("[CategorySearchSourceService] No sources found for category {} at any level", category.getName());
        return List.of();
    }

    // getCoreFields() entfällt — Pflichtfelder kommen aus CategorySpecPreferenceService
    // (P2b: getMandatoryFields() = SYSTEM + USER)
}
