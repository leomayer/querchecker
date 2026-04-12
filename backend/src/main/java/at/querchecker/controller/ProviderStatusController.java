package at.querchecker.controller;

import at.querchecker.config.ProviderStatus;
import at.querchecker.config.ProviderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gibt den aktuellen Provider-Status zurück.
 * Primär via SSE gepusht — REST-Endpoint für direkten Abruf (z.B. nach F5).
 */
@RestController
@RequestMapping("/api/provider-status")
@RequiredArgsConstructor
public class ProviderStatusController {

    private final ProviderStatusService providerStatusService;

    @GetMapping
    public ProviderStatus getStatus() {
        return providerStatusService.getStatus();
    }
}
