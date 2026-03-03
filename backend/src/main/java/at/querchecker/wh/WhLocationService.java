package at.querchecker.wh;

import at.querchecker.dto.WhLocationDto;
import at.querchecker.entity.AppConfig;
import at.querchecker.entity.WhLocation;
import at.querchecker.repository.AppConfigRepository;
import at.querchecker.repository.WhLocationRepository;
import at.querchecker.wh.api.WhApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhLocationService {

    private static final String LAST_FETCHED_KEY = "wh.locations.last_fetched_at";
    private static final String NAVIGATOR_URL =
        "https://www.willhaben.at/webapi/iad/search/atz/seo/kaufen-und-verkaufen/marktplatz?rows=1&page=1";

    private final WhApiClient whApiClient;
    private final WhLocationRepository whLocationRepository;
    private final AppConfigRepository appConfigRepository;

    public boolean isEmpty() {
        return whLocationRepository.count() == 0;
    }

    /** Gibt den Standort-Baum aus der DB zurück (Bundesland → Bezirk). */
    public List<WhLocationDto> getTree() {
        return whLocationRepository.findByLevelOrderByNameAsc(0)
            .stream()
            .map(this::toDto)
            .toList();
    }

    private WhLocationDto toDto(WhLocation loc) {
        List<WhLocationDto> children = whLocationRepository
            .findByParentIdOrderByNameAsc(loc.getId())
            .stream()
            .map(this::toDto)
            .toList();
        return WhLocationDto.builder()
            .areaId(loc.getAreaId())
            .name(loc.getName())
            .level(loc.getLevel())
            .children(children)
            .build();
    }

    /**
     * Ruft den Standort-Navigator von Willhaben ab und aktualisiert die DB.
     * Verarbeitet 2 Ebenen: Bundesland → Bezirk.
     */
    @Transactional
    public void fetchAndUpsert() {
        log.info("Starte Standort-Aktualisierung...");
        try {
            WhApiResponse.Root response = whApiClient
                .get(URI.create(NAVIGATOR_URL), WhApiResponse.Root.class)
                .getBody();

            if (response == null || response.getNavigatorList() == null
                    || response.getNavigatorList().getNavigator() == null) {
                log.warn("Kein Navigator in der Willhaben-Antwort – Abbruch");
                return;
            }

            // Standort-Navigator erkennen: NavigatorValues haben urlParameterName = "areaId"
            response.getNavigatorList().getNavigator().stream()
                .filter(n -> n.getNavigatorValue() != null)
                .filter(n -> n.getNavigatorValue().stream()
                    .anyMatch(v -> "areaId".equals(v.getUrlParameterName())))
                .findFirst()
                .ifPresentOrElse(
                    nav -> processLevel(nav.getNavigatorValue(), null, 0),
                    () -> log.warn("Kein areaId-Navigator gefunden")
                );

            saveLastFetched();
            log.info("Standort-Aktualisierung abgeschlossen ({} Einträge)", whLocationRepository.count());
        } catch (Exception e) {
            log.error("Fehler bei der Standort-Aktualisierung", e);
        }
    }

    private void processLevel(List<WhApiResponse.NavigatorValue> values, WhLocation parent, int level) {
        if (values == null || level > 1) return;
        for (WhApiResponse.NavigatorValue val : values) {
            Integer areaId = parseId(val.getUrlParameterValue());
            if (areaId == null || val.getLabel() == null) continue;

            WhLocation loc = whLocationRepository.findByAreaId(areaId)
                .orElse(WhLocation.builder().areaId(areaId).build());
            loc.setName(val.getLabel());
            loc.setLevel(level);
            loc.setParent(parent);
            WhLocation saved = whLocationRepository.save(loc);

            if (val.getSubNavigatorList() != null && val.getSubNavigatorList().getNavigator() != null) {
                for (WhApiResponse.Navigator subNav : val.getSubNavigatorList().getNavigator()) {
                    processLevel(subNav.getNavigatorValue(), saved, level + 1);
                }
            }
        }
    }

    private void saveLastFetched() {
        LocalDateTime now = LocalDateTime.now();
        AppConfig config = appConfigRepository.findById(LAST_FETCHED_KEY)
            .orElse(AppConfig.builder().key(LAST_FETCHED_KEY).description("Letzter Standort-Abruf").build());
        config.setValue(now.toString());
        config.setUpdatedAt(now);
        appConfigRepository.save(config);
    }

    private static Integer parseId(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
