package at.querchecker.controller;

import at.querchecker.dto.QuercheckerNoteDto;
import at.querchecker.service.ListingNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/listings/{listingId}/notes")
@RequiredArgsConstructor
@Tag(name = "Notes", description = "Notizen zu Inseraten")
public class ListingNoteController {

    private final ListingNoteService listingNoteService;

    @GetMapping
    @Operation(summary = "Alle Notizen zu einem Inserat")
    public List<QuercheckerNoteDto> findByListing(@PathVariable Long listingId) {
        return listingNoteService.findByListingId(listingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Neue Notiz erstellen")
    public QuercheckerNoteDto create(@PathVariable Long listingId,
                                    @RequestBody QuercheckerNoteDto dto) {
        return listingNoteService.create(listingId, dto);
    }

    @PutMapping("/{noteId}")
    @Operation(summary = "Notiz aktualisieren")
    public QuercheckerNoteDto update(@PathVariable Long listingId,
                                    @PathVariable Long noteId,
                                    @RequestBody QuercheckerNoteDto dto) {
        return listingNoteService.update(noteId, dto);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Notiz löschen")
    public void delete(@PathVariable Long listingId,
                       @PathVariable Long noteId) {
        listingNoteService.delete(noteId);
    }
}
