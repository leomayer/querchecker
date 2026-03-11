package at.querchecker.controller;

import at.querchecker.dto.WhItemDto;
import at.querchecker.service.WhListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
@Tag(name = "Listings", description = "Willhaben-Inserate verwalten")
public class WhListingController {

    private final WhListingService whListingService;

    @GetMapping
    @Operation(summary = "Alle Inserate abrufen")
    public List<WhItemDto> findAll(
            @RequestParam(defaultValue = "UP") String ratingFilter) {
        return whListingService.findAll(ratingFilter);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inserat nach ID abrufen")
    public ResponseEntity<WhItemDto> findById(@PathVariable Long id) {
        return whListingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Neues Inserat speichern")
    public WhItemDto create(@RequestBody WhItemDto dto) {
        return whListingService.save(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inserat löschen")
    public void delete(@PathVariable Long id) {
        whListingService.deleteById(id);
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "Inserate nach Rating und Alter löschen")
    public Map<String, Integer> cleanup(
            @RequestParam String rating,
            @RequestParam(defaultValue = "30") int olderThanDays) {
        int deleted = whListingService.cleanupByRating(rating, olderThanDays);
        return Map.of("deleted", deleted);
    }
}
