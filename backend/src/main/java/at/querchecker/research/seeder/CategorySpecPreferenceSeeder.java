package at.querchecker.research.seeder;

import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.entity.CategorySpecPreferenceField;
import at.querchecker.research.entity.FieldSource;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Seedet SYSTEM-Pflichtfelder je Kategorie.
 * Nur generische Spec-Labels (cpu, ram) — keine Wert-Keywords (oled, thunderbolt4),
 * da SYSTEM-Felder nicht in Brave-Queries einfließen.
 *
 * Additive Logik: fehlende SYSTEM-Felder werden ergänzt, bestehende nie überschrieben.
 * Aufruf: nach WhRefreshScheduler (Kategorien müssen in DB sein).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CategorySpecPreferenceSeeder {

    private final CategorySpecPreferenceRepository repo;
    private final WhCategoryRepository categoryRepo;

    // Kategorienamen müssen exakt mit WhCategory.name übereinstimmen.
    // Mehrfacheinträge (z.B. "Tablets" auf level=1 und level=2) werden alle geseedet.
    private static final Map<String, List<String>> SYSTEM_FIELDS = Map.ofEntries(
        entry("Notebooks",            List.of("cpu", "ram", "display", "storage", "gpu", "battery", "weight", "release_year")),
        entry("Smartphones / Handys", List.of("cpu", "ram", "storage", "display", "battery", "camera", "release_year")),
        entry("Tablets",              List.of("cpu", "ram", "storage", "display", "release_year")),
        entry("Monitore",             List.of("screen_size", "resolution", "panel_type", "refresh_rate", "release_year")),
        entry("Beamer",               List.of("resolution", "technology", "release_year")),
        entry("Drucker",              List.of("technology", "duplex", "ppm_mono", "release_year")),
        entry("PC-Komponenten",       List.of("type", "interface", "capacity")),
        entry("Netzwerke",            List.of("type", "ports", "throughput")),
        entry("Kameras / Camcorder",  List.of("sensor", "megapixel", "lens_mount", "release_year")),
        entry("Fernseher",            List.of("screen_size", "panel_type", "resolution", "hdmi", "refresh_rate", "release_year")),
        entry("Konsolen",             List.of("platform", "storage", "release_year"))
    );

    @Transactional
    public void seedIfAbsent() {
        if (categoryRepo.count() == 0) {
            log.warn("CategorySpecPreferenceSeeder: wh_category leer — übersprungen");
            return;
        }

        SYSTEM_FIELDS.forEach((categoryName, fields) -> {
            List<WhCategory> matches = categoryRepo.findAllByName(categoryName);
            if (matches.isEmpty()) {
                log.warn("CategorySpecPreferenceSeeder: Kategorie '{}' nicht in DB — übersprungen", categoryName);
            } else {
                matches.forEach(cat -> seedForCategory(cat, fields));
            }
        });
    }

    private void seedForCategory(WhCategory cat, List<String> systemFields) {
        CategorySpecPreference pref = repo.findByWhCategory(cat)
                .orElseGet(() -> {
                    CategorySpecPreference p = new CategorySpecPreference();
                    p.setWhCategory(cat);
                    return repo.save(p);
                });

        for (String fieldKey : systemFields) {
            boolean exists = pref.getFields().stream()
                    .anyMatch(f -> f.getFieldKey().equals(fieldKey)
                               && f.getFieldSource() == FieldSource.SYSTEM);
            if (!exists) {
                pref.getFields().add(CategorySpecPreferenceField.builder()
                        .preference(pref)
                        .fieldKey(fieldKey)
                        .fieldSource(FieldSource.SYSTEM)
                        .build());
                log.info("CategorySpecPreferenceSeeder: '{}' → SYSTEM-Feld '{}' geseedet",
                        cat.getName(), fieldKey);
            }
        }
        repo.save(pref);
    }
}
