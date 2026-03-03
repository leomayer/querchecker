package at.querchecker.wh;

import at.querchecker.dto.WhCategoryDto;
import at.querchecker.dto.WhLocationDto;
import at.querchecker.dto.WhMetaStatusDto;
import at.querchecker.repository.AppConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/wh/meta")
@RequiredArgsConstructor
@Tag(name = "Willhaben Meta", description = "Kategorien und Standorte verwalten")
public class WhMetaController {

    private final WhCategoryService whCategoryService;
    private final WhLocationService whLocationService;
    private final WhRefreshScheduler whRefreshScheduler;
    private final AppConfigRepository appConfigRepository;

    @GetMapping("/status")
    @Operation(summary = "Status der letzten Aktualisierungen und des laufenden Refresh")
    public ResponseEntity<WhMetaStatusDto> status() {
        return ResponseEntity.ok(WhMetaStatusDto.builder()
            .categoriesLastFetched(readTimestamp("wh.categories.last_fetched_at"))
            .locationsLastFetched(readTimestamp("wh.locations.last_fetched_at"))
            .refreshInProgress(whRefreshScheduler.isRefreshInProgress())
            .refreshCron(whRefreshScheduler.getRefreshCron())
            .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Manuellen Aktualisierungs-Lauf auslösen (asynchron)")
    public ResponseEntity<Void> refresh() {
        Thread.ofVirtual().start(whRefreshScheduler::runRefresh);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/categories")
    @Operation(summary = "Kategorie-Baum (bis zu 3 Ebenen) aus der DB")
    public ResponseEntity<List<WhCategoryDto>> categories() {
        return ResponseEntity.ok(whCategoryService.getTree());
    }

    @GetMapping("/locations")
    @Operation(summary = "Standort-Baum (Bundesland → Bezirk) aus der DB")
    public ResponseEntity<List<WhLocationDto>> locations() {
        return ResponseEntity.ok(whLocationService.getTree());
    }

    private LocalDateTime readTimestamp(String key) {
        return appConfigRepository.findById(key)
            .map(c -> {
                try { return LocalDateTime.parse(c.getValue()); } catch (Exception e) { return null; }
            })
            .orElse(null);
    }
}
