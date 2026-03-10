package at.querchecker.controller;

import at.querchecker.dto.WhListingDetailDto;
import at.querchecker.service.WhListingDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings/{id}")
@RequiredArgsConstructor
@Tag(name = "ListingDetail", description = "Benutzereigene Details zu Inseraten")
public class WhListingDetailController {

    private final WhListingDetailService whListingDetailService;

    @GetMapping("/detail")
    @Operation(summary = "Details zu einem Inserat abrufen")
    public WhListingDetailDto getDetail(@PathVariable Long id) {
        return whListingDetailService.getDetail(id);
    }

    @PutMapping("/detail/note")
    @Operation(summary = "Notiz zu einem Inserat speichern")
    public WhListingDetailDto updateNote(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return whListingDetailService.updateNote(id, body.get("note"));
    }

    @PutMapping("/detail/rating")
    @Operation(summary = "Bewertung eines Inserats speichern (UP/DOWN/null)")
    public WhListingDetailDto updateRating(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        return whListingDetailService.updateRating(id, body.get("rating"));
    }

    @PutMapping("/detail/interest")
    @Operation(summary = "Interesse-Level eines Inserats speichern (LOW/MEDIUM/HIGH/null)")
    public WhListingDetailDto updateInterest(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        return whListingDetailService.updateInterest(id, body.get("level"));
    }

    @PutMapping("/detail/tags")
    @Operation(summary = "Tags eines Inserats speichern")
    public WhListingDetailDto updateTags(@PathVariable Long id,
                                        @RequestBody Map<String, List<String>> body) {
        return whListingDetailService.updateTags(id, body.getOrDefault("tags", List.of()));
    }

    @PostMapping("/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aufruf eines Inserats erfassen")
    public void recordView(@PathVariable Long id) {
        whListingDetailService.recordView(id);
    }
}
