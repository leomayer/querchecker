package at.querchecker.wh;

import at.querchecker.dto.QuercheckerListingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wh")
@RequiredArgsConstructor
@Tag(name = "Willhaben Search", description = "Willhaben durchsuchen und Ergebnisse speichern")
public class WhSearchController {

    private final WhSearchService whSearchService;

    /**
     * Führt eine Willhaben-Suche durch, upsertet alle Treffer in die DB und
     * gibt sie zurück.
     *
     * @param keyword Suchbegriff, z.B. "rtx 4070"
     * @param rows    Anzahl der Ergebnisse (default: 30)
     */
    /**
     * @param keyword       Suchbegriff, z.B. "rtx 4070"
     * @param rows          Anzahl der Ergebnisse (default: 30)
     * @param attributeTree Willhaben ATTRIBUTE_TREE-ID (Kategorie-Filter, optional)
     * @param areaId        Willhaben areaId (Standort-Filter, optional)
     */
    @GetMapping("/search")
    @Operation(summary = "Willhaben durchsuchen und Ergebnisse in DB speichern")
    public ResponseEntity<List<QuercheckerListingDto>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int rows,
            @RequestParam(required = false) Integer priceFrom,
            @RequestParam(required = false) Integer priceTo,
            @RequestParam(required = false) Integer attributeTree,
            @RequestParam(required = false) Integer areaId) {

        return ResponseEntity.ok(
            whSearchService.search(keyword, rows, priceFrom, priceTo, attributeTree, areaId));
    }
}
