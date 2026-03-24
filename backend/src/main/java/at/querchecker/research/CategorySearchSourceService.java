package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.repository.CategorySearchSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorySearchSourceService {

    private final CategorySearchSourceRepository repo;

    /**
     * Liefert aktive, lookup-enabled Quellen für eine Kategorie.
     * Suchreihenfolge:
     * 1. Eigener level-2-Eintrag mit lookupEnabled=true
     * 2. Eigener Eintrag mit lookupEnabled=false → leere Liste (kein Lookup)
     * 3. Level-1-Elterneintrag mit inheritFromParent=true
     * 4. Nichts → leere Liste
     */
    public List<CategorySearchSource> findForCategory(WhCategory category) {
        if (category == null) return List.of();

        List<CategorySearchSource> own =
                repo.findByWhCategoryAndActiveTrueOrderByPriorityAsc(category);

        if (!own.isEmpty()) {
            // lookupEnabled=false → kein Lookup für diese Kategorie
            return own.stream()
                    .filter(CategorySearchSource::isLookupEnabled)
                    .toList();
        }

        // Level-1-Fallback — nur wenn explizit opt-in
        if (category.getParent() != null) {
            return repo.findByWhCategoryAndInheritFromParentTrueAndActiveTrueOrderByPriorityAsc(
                            category.getParent())
                    .stream()
                    .filter(CategorySearchSource::isLookupEnabled)
                    .toList();
        }

        return List.of();
    }

    // getCoreFields() entfällt — Pflichtfelder kommen aus CategorySpecPreferenceService
    // (P2b: getMandatoryFields() = SYSTEM + USER)
}
