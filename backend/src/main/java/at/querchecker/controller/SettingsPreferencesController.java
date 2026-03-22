package at.querchecker.controller;

import at.querchecker.controller.dto.PreferenceRequest;
import at.querchecker.controller.dto.PreferenceResponse;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.research.CategorySpecPreferenceService;
import at.querchecker.research.entity.CategorySpecPreference;
import at.querchecker.research.repository.CategorySpecPreferenceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/settings/preferences")
@RequiredArgsConstructor
@Tag(name = "SettingsPreferences", description = "Kategorie-spezifische Spec-Präferenzen")
public class SettingsPreferencesController {

    private final WhCategoryRepository categoryRepository;
    private final CategorySpecPreferenceService preferenceService;
    private final CategorySpecPreferenceRepository preferenceRepository;

    @GetMapping
    @Operation(summary = "Alle gespeicherten Kategorie-Präferenzen abrufen")
    public List<PreferenceResponse> getAll() {
        return preferenceRepository.findAll().stream()
                .map(p -> new PreferenceResponse(
                        p.getWhCategory().getId(),
                        p.getWhCategory().getName(),
                        p.getFieldKeys()))
                .toList();
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Spec-Präferenzen für eine Kategorie speichern (max. 5 Keywords)")
    public PreferenceResponse setPreferences(@PathVariable Long categoryId,
                                             @RequestBody PreferenceRequest req) {
        WhCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kategorie nicht gefunden: " + categoryId));

        try {
            preferenceService.setPreferences(category, req.getFieldKeys());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return new PreferenceResponse(category.getId(), category.getName(), req.getFieldKeys());
    }
}
