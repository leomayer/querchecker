package at.querchecker.wh;

import at.querchecker.dto.WhCategoryDto;
import at.querchecker.entity.AppConfig;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.AppConfigRepository;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.wh.api.WhApiResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhCategoryService {

    private static final String LAST_FETCHED_KEY =
        "wh.categories.last_fetched_at";
    private static final String NAVIGATOR_URL =
        "https://www.willhaben.at/webapi/iad/search/atz/seo/kaufen-und-verkaufen/marktplatz?rows=1&page=1";

    private final WhApiClient whApiClient;
    private final WhCategoryRepository whCategoryRepository;
    private final AppConfigRepository appConfigRepository;

    public boolean isEmpty() {
        return whCategoryRepository.count() == 0;
    }

    /** Gibt den Kategorie-Baum aus der DB zurück (3 Ebenen). */
    public List<WhCategoryDto> getTree() {
        return whCategoryRepository
            .findByLevelOrderByNameAsc(0)
            .stream()
            .map(this::toDto)
            .toList();
    }

    private WhCategoryDto toDto(WhCategory cat) {
        List<WhCategoryDto> children = whCategoryRepository
            .findByParentIdOrderByNameAsc(cat.getId())
            .stream()
            .map(this::toDto)
            .toList();
        return WhCategoryDto.builder()
            .whId(cat.getWhId())
            .name(cat.getName())
            .level(cat.getLevel())
            .children(children)
            .build();
    }

    /**
     * Ruft den Kategorie-Navigator von Willhaben ab und aktualisiert die DB.
     * Stufe 0: alle Top-Level-Kategorien; Stufe 1: Unterkategorien; Stufe 2: Unter-Unterkategorien.
     */
    public void fetchAndUpsert() {
        log.info("Starte Kategorie-Aktualisierung...");
        try {
            WhApiResponse.Root response = whApiClient
                .get(URI.create(NAVIGATOR_URL), WhApiResponse.Root.class)
                .getBody();

            if (response == null || response.getNavigatorGroups() == null) {
                log.warn(
                    "Kein Kategorie-Navigator in der Willhaben-Antwort gefunden – Abbruch"
                );
                return;
            }

            List<WhApiResponse.PossibleValue> topValues =
                extractByParam(response, "ATTRIBUTE_TREE");

            if (topValues.isEmpty()) {
                log.warn("Keine Kategorien (ATTRIBUTE_TREE) in der Willhaben-Antwort gefunden");
                return;
            }

            List<WhCategory> topCategories = saveTopLevel(topValues);
            log.info("{} Top-Level-Kategorien gespeichert, starte Unterkategorie-Abruf...", topCategories.size());

            for (WhCategory parent : topCategories) {
                List<WhCategory> subCategories = fetchAndSaveSubcategories(parent);
                log.info("  {} Unterkategorien für '{}', starte Ebene-2-Abruf...", subCategories.size(), parent.getName());
                for (WhCategory sub : subCategories) {
                    fetchAndSaveSubSubcategories(parent, sub);
                }
            }

            saveLastFetched();
            log.info(
                "Kategorie-Aktualisierung abgeschlossen ({} Einträge gesamt)",
                whCategoryRepository.count()
            );
        } catch (Exception e) {
            log.error("Fehler bei der Kategorie-Aktualisierung", e);
        }
    }

    private List<WhCategory> saveTopLevel(List<WhApiResponse.PossibleValue> values) {
        List<WhCategory> saved = new ArrayList<>();
        for (WhApiResponse.PossibleValue val : values) {
            Integer whId = parseId(val.getUrlParameterValue());
            if (whId == null || val.getLabel() == null) continue;

            WhCategory cat = whCategoryRepository
                .findByWhId(whId)
                .orElse(WhCategory.builder().whId(whId).build());
            cat.setName(val.getLabel());
            cat.setLevel(0);
            cat.setParent(null);
            saved.add(whCategoryRepository.save(cat));
        }
        return saved;
    }

    private List<WhCategory> fetchAndSaveSubcategories(WhCategory parent) {
        List<WhCategory> saved = new ArrayList<>();
        try {
            String url = NAVIGATOR_URL + "&ATTRIBUTE_TREE=" + parent.getWhId();
            WhApiResponse.Root response = whApiClient
                .get(URI.create(url), WhApiResponse.Root.class)
                .getBody();

            if (response == null || response.getNavigatorGroups() == null) return saved;

            List<WhApiResponse.PossibleValue> subValues =
                extractByParam(response, "ATTRIBUTE_TREE");

            for (WhApiResponse.PossibleValue val : subValues) {
                Integer whId = parseId(val.getUrlParameterValue());
                if (whId == null || val.getLabel() == null || whId.equals(parent.getWhId())) continue;

                WhCategory existing = whCategoryRepository.findByWhId(whId).orElse(null);
                if (existing != null && existing.getLevel() < 1) continue; // nie Level-0 überschreiben
                WhCategory cat = existing != null ? existing : WhCategory.builder().whId(whId).build();
                cat.setName(val.getLabel());
                cat.setLevel(1);
                cat.setParent(parent);
                saved.add(whCategoryRepository.save(cat));
            }
        } catch (Exception e) {
            log.error("Fehler beim Abruf von Unterkategorien für {} ({})",
                parent.getName(), parent.getWhId(), e);
        }
        return saved;
    }

    private void fetchAndSaveSubSubcategories(WhCategory topParent, WhCategory subParent) {
        try {
            String url = NAVIGATOR_URL
                + "&ATTRIBUTE_TREE=" + topParent.getWhId()
                + "&ATTRIBUTE_TREE=" + subParent.getWhId();
            WhApiResponse.Root response = whApiClient
                .get(URI.create(url), WhApiResponse.Root.class)
                .getBody();

            if (response == null || response.getNavigatorGroups() == null) return;

            List<WhApiResponse.PossibleValue> subValues =
                extractByParam(response, "ATTRIBUTE_TREE");

            for (WhApiResponse.PossibleValue val : subValues) {
                Integer whId = parseId(val.getUrlParameterValue());
                if (whId == null || val.getLabel() == null
                    || whId.equals(topParent.getWhId())
                    || whId.equals(subParent.getWhId())) continue;

                WhCategory existing = whCategoryRepository.findByWhId(whId).orElse(null);
                if (existing != null && existing.getLevel() < 2) continue; // nie Level-0/1 überschreiben
                WhCategory cat = existing != null ? existing : WhCategory.builder().whId(whId).build();
                cat.setName(val.getLabel());
                cat.setLevel(2);
                cat.setParent(subParent);
                whCategoryRepository.save(cat);
            }
        } catch (Exception e) {
            log.error("Fehler beim Abruf von Unter-Unterkategorien für {} > {} ({} > {})",
                topParent.getName(), subParent.getName(), topParent.getWhId(), subParent.getWhId(), e);
        }
    }

    private List<WhApiResponse.PossibleValue> extractByParam(
        WhApiResponse.Root response, String paramName
    ) {
        return response.getNavigatorGroups()
            .stream()
            .filter(g -> g.getNavigatorList() != null)
            .flatMap(g -> g.getNavigatorList().stream())
            .filter(n -> n.getGroupedPossibleValues() != null)
            .flatMap(n -> n.getGroupedPossibleValues().stream())
            .filter(gpv -> gpv.getPossibleValues() != null)
            .flatMap(gpv -> gpv.getPossibleValues().stream())
            .filter(v -> paramName.equals(v.getUrlParameterName()))
            .toList();
    }

    private void saveLastFetched() {
        LocalDateTime now = LocalDateTime.now();
        AppConfig config = appConfigRepository
            .findById(LAST_FETCHED_KEY)
            .orElse(
                AppConfig.builder()
                    .key(LAST_FETCHED_KEY)
                    .description("Letzter Kategorien-Abruf")
                    .build()
            );
        config.setValue(now.toString());
        config.setUpdatedAt(now);
        appConfigRepository.save(config);
    }

    private static Integer parseId(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
