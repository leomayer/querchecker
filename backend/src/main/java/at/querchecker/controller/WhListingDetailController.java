package at.querchecker.controller;

import at.querchecker.dto.WhDetailDto;
import at.querchecker.service.WhItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings/{id}")
@RequiredArgsConstructor
@Tag(name = "ListingDetail", description = "Benutzereigene Details zu Inseraten")
public class WhListingDetailController {

    private final WhItemService whItemService;

    @PostMapping("/detail")
    @Operation(summary = "Details zu einem Inserat abrufen und Aufruf erfassen")
    public WhDetailDto openDetail(@PathVariable Long id) {
        return whItemService.openDetail(id);
    }

    @PutMapping("/detail/note")
    @Operation(summary = "Notiz zu einem Inserat speichern")
    public WhDetailDto updateNote(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return whItemService.updateNote(id, body.get("note"));
    }

    @PutMapping("/detail/rating")
    @Operation(summary = "Bewertung eines Inserats speichern (UP/DOWN/null)")
    public WhDetailDto updateRating(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        return whItemService.updateRating(id, body.get("rating"));
    }

    @PutMapping("/detail/interest")
    @Operation(summary = "Interesse-Level eines Inserats speichern (LOW/MEDIUM/HIGH/null)")
    public WhDetailDto updateInterest(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        return whItemService.updateInterest(id, body.get("level"));
    }

    @PutMapping("/detail/tags")
    @Operation(summary = "Tags eines Inserats speichern")
    public WhDetailDto updateTags(@PathVariable Long id,
                                        @RequestBody Map<String, List<String>> body) {
        return whItemService.updateTags(id, body.getOrDefault("tags", List.of()));
    }

}
