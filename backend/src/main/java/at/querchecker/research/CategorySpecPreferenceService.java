package at.querchecker.research;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.entity.CategorySpecPreferenceField;
import at.querchecker.research.entity.FieldSource;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Verwaltet kategoriespezifische Spezifikations-Präferenzen.
 * Vererbungslogik: eigene Präferenz → Eltern-Ebene → ... → null (leer)
 *
 * Feldquellen:
 *   SYSTEM — geseedet, generic spec labels (cpu, ram) → nur ans LLM
 *   USER   — user-gepflegt, value keywords (OLED, Core i7) → Brave + LLM
 */
@Service
@RequiredArgsConstructor
public class CategorySpecPreferenceService {

    private final CategorySpecPreferenceRepository repo;

    /**
     * USER-Felder als Brave-Query-Keywords (≤5).
     * Nur VALUE-Keywords (user-gepflegt) gehen in die Suche.
     */
    public List<String> getQueryKeywords(WhCategory whCategory) {
        CategorySpecPreference pref = findWithInheritance(whCategory);
        if (pref == null) return List.of();
        return pref.getFields().stream()
                .filter(f -> f.getFieldSource() == FieldSource.USER)
                .map(CategorySpecPreferenceField::getFieldKey)
                .limit(5)
                .toList();
    }

    /**
     * Nur SYSTEM-Felder — für LLM-Pflichtfeldextraktion und Qualitätsbewertung.
     * USER-Keywords sind Wert-Begriffe (oled, thunderbolt4), keine quickFacts-Keys,
     * und würden die Abdeckungsberechnung verfälschen.
     */
    public List<String> getMandatorySystemFields(WhCategory whCategory) {
        CategorySpecPreference pref = findWithInheritance(whCategory);
        if (pref == null) return List.of();
        return pref.getFields().stream()
                .filter(f -> f.getFieldSource() == FieldSource.SYSTEM)
                .map(CategorySpecPreferenceField::getFieldKey)
                .toList();
    }

    /**
     * SYSTEM + USER-Felder als kombinierte LLM-Pflichtfelder (alle, ohne Limit).
     * Wird in P8 durch getMandatorySystemFields() ersetzt — bis dahin als Übergang.
     */
    public List<String> getMandatoryFields(WhCategory whCategory) {
        CategorySpecPreference pref = findWithInheritance(whCategory);
        if (pref == null) return List.of();
        return pref.getFields().stream()
                .map(CategorySpecPreferenceField::getFieldKey)
                .toList();
    }

    /**
     * Speichert USER-Felder für eine Kategorie (ersetzt bestehende USER-Felder).
     * SYSTEM-Felder werden nicht berührt.
     *
     * @throws IllegalArgumentException wenn mehr als 5 USER-Keywords übergeben werden
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

        pref.getFields().removeIf(f -> f.getFieldSource() == FieldSource.USER);
        for (String key : fieldKeys) {
            pref.getFields().add(CategorySpecPreferenceField.builder()
                    .preference(pref)
                    .fieldKey(key)
                    .fieldSource(FieldSource.USER)
                    .build());
        }
        repo.save(pref);
    }

    // --- private helpers ---

    private CategorySpecPreference findWithInheritance(WhCategory category) {
        if (category == null) return null;
        return repo.findByWhCategory(category)
                .orElseGet(() -> findWithInheritance(category.getParent()));
    }
}
