package at.querchecker.wh;

import at.querchecker.dto.WhCategoryDto;
import at.querchecker.entity.AppConfig;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.AppConfigRepository;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.wh.api.WhApiResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Verarbeitet bis zu 3 Ebenen aus der verschachtelten Navigator-Antwort.
     */
    @Transactional
    public void fetchAndUpsert() {
        log.info("Starte Kategorie-Aktualisierung...");
        try {
            WhApiResponse.Root response = whApiClient
                .get(URI.create(NAVIGATOR_URL), WhApiResponse.Root.class)
                .getBody();

            if (
                response == null ||
                response.getNavigatorList() == null ||
                response.getNavigatorList().getNavigator() == null
            ) {
                log.warn(
                    "Kein Kategorie-Navigator in der Willhaben-Antwort gefunden – Abbruch"
                );
                return;
            }

            // Kategorie-Navigator erkennen: NavigatorValues haben urlParameterName = "ATTRIBUTE_TREE"
            response
                .getNavigatorList()
                .getNavigator()
                .stream()
                .filter(n -> n.getNavigatorValue() != null)
                .filter(n ->
                    n
                        .getNavigatorValue()
                        .stream()
                        .anyMatch(v ->
                            "ATTRIBUTE_TREE".equals(v.getUrlParameterName())
                        )
                )
                .findFirst()
                .ifPresentOrElse(
                    nav -> processLevel(nav.getNavigatorValue(), null, 0),
                    () -> log.warn("Kein ATTRIBUTE_TREE-Navigator gefunden")
                );

            saveLastFetched();
            log.info(
                "Kategorie-Aktualisierung abgeschlossen ({} Einträge)",
                whCategoryRepository.count()
            );
        } catch (Exception e) {
            log.error("Fehler bei der Kategorie-Aktualisierung", e);
        }
    }

    private void processLevel(
        List<WhApiResponse.NavigatorValue> values,
        WhCategory parent,
        int level
    ) {
        if (values == null || level > 2) return;
        for (WhApiResponse.NavigatorValue val : values) {
            Integer whId = parseId(val.getUrlParameterValue());
            if (whId == null || val.getLabel() == null) continue;

            WhCategory cat = whCategoryRepository
                .findByWhId(whId)
                .orElse(WhCategory.builder().whId(whId).build());
            cat.setName(val.getLabel());
            cat.setLevel(level);
            cat.setParent(parent);
            WhCategory saved = whCategoryRepository.save(cat);

            if (
                val.getSubNavigatorList() != null &&
                val.getSubNavigatorList().getNavigator() != null
            ) {
                for (WhApiResponse.Navigator subNav : val
                    .getSubNavigatorList()
                    .getNavigator()) {
                    processLevel(subNav.getNavigatorValue(), saved, level + 1);
                }
            }
        }
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
