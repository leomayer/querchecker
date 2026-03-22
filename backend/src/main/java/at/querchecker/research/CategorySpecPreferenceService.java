package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet kategoriespezifische Spezifikations-Präferenzen.
 * Vererbungslogik: eigene Präferenz → Eltern-Ebene → ... → null (leer)
 */
@Service
@RequiredArgsConstructor
public class CategorySpecPreferenceService {

    private final CategorySpecPreferenceRepository repo;

    /**
     * Liefert die Präferenz-Keywords für eine Kategorie (mit rekursiver Vererbung).
     * @return leere Liste wenn keine Präferenz in der gesamten Hierarchie vorhanden
     */
    public List<String> getPreferences(WhCategory whCategory) {
        CategorySpecPreference pref = findWithInheritance(whCategory);
        return pref != null ? pref.getFieldKeys() : List.of();
    }

    /**
     * Speichert Präferenz-Keywords für eine Kategorie.
     * @throws IllegalArgumentException wenn mehr als 5 Keywords übergeben werden
     */
    public void setPreferences(WhCategory whCategory, List<String> fieldKeys) {
        if (fieldKeys.size() > 5) {
            throw new IllegalArgumentException(
                    "Maximal 5 Keywords erlaubt, erhalten: " + fieldKeys.size());
        }
        CategorySpecPreference pref = repo.findByWhCategory(whCategory)
                .orElseGet(() -> {
                    CategorySpecPreference p = new CategorySpecPreference();
                    p.setWhCategory(whCategory);
                    return p;
                });
        pref.setFieldKeys(new ArrayList<>(fieldKeys));
        repo.save(pref);
    }

    /** Erste 5 Keywords für die Brave-Query (Stufe 1). */
    public List<String> getQueryKeywords(WhCategory whCategory) {
        return getPreferences(whCategory).stream().limit(5).toList();
    }

    /** Alle Keywords als Pflichtfelder für den LLM-Prompt. */
    public List<String> getMandatoryFields(WhCategory whCategory) {
        return getPreferences(whCategory);
    }

    // --- private helpers ---

    private CategorySpecPreference findWithInheritance(WhCategory category) {
        if (category == null) return null;
        return repo.findByWhCategory(category)
                .orElseGet(() -> findWithInheritance(category.getParent()));
    }
}
