package at.querchecker.controller;

import at.querchecker.dto.QuercheckerListingDto;
import at.querchecker.service.WhListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
@Tag(name = "Listings", description = "Willhaben-Inserate verwalten")
public class WhListingController {

    private final WhListingService whListingService;

    @GetMapping
    @Operation(summary = "Alle Inserate abrufen")
    public List<QuercheckerListingDto> findAll() {
        return whListingService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inserat nach ID abrufen")
    public ResponseEntity<QuercheckerListingDto> findById(@PathVariable Long id) {
        return whListingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Neues Inserat speichern")
    public QuercheckerListingDto create(@RequestBody QuercheckerListingDto dto) {
        return whListingService.save(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inserat löschen")
    public void delete(@PathVariable Long id) {
        whListingService.deleteById(id);
    }
}
