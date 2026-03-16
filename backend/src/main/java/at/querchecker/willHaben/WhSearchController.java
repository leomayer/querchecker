package at.querchecker.willHaben;

import at.querchecker.dto.WhSearchResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wh")
@RequiredArgsConstructor
@Tag(name = "Willhaben Search", description = "Willhaben durchsuchen und Ergebnisse speichern")
public class WhSearchController {

    private final WhSearchService whSearchService;

    /**
     * @param keyword       Suchbegriff, z.B. "rtx 4070"
     * @param rows          Anzahl der Ergebnisse (default: 30)
     * @param attributeTree Willhaben ATTRIBUTE_TREE-ID (Kategorie-Filter, optional)
     * @param areaId        Willhaben areaId (Standort-Filter, optional)
     * @param paylivery     Nur Paylivery-Angebote (optional)
     */
    @GetMapping("/search")
    @Operation(summary = "Willhaben durchsuchen und Ergebnisse in DB speichern")
    public ResponseEntity<WhSearchResultDto> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "30") int rows,
            @RequestParam(required = false) Integer priceFrom,
            @RequestParam(required = false) Integer priceTo,
            @RequestParam(required = false) Integer attributeTree,
            @RequestParam(required = false) Integer areaId,
            @RequestParam(required = false) Boolean paylivery) {

        return ResponseEntity.ok(
            whSearchService.search(keyword, rows, priceFrom, priceTo, attributeTree, areaId, paylivery));
    }
}
