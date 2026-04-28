package at.querchecker.controller;

import at.querchecker.config.ProviderState;
import at.querchecker.config.ProviderStatus;
import at.querchecker.config.ProviderStatusService;
import at.querchecker.config.ProviderTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ProviderTestService providerTestService;

    @GetMapping
    public ProviderStatus getStatus() {
        return providerStatusService.getStatus();
    }

    /**
     * Minimaler Verbindungstest für den angegebenen Provider.
     * Nur bei CONFIGURED, UNREACHABLE oder UNAVAILABLE sinnvoll — bei anderen Zuständen no-op.
     * Die Statusaktualisierung erfolgt via SSE; der Response-Body spiegelt den neuen Zustand.
     */
    @PostMapping("/test")
    public ProviderStatus testProvider(@RequestParam String provider) {
        ProviderStatus current = providerStatusService.getStatus();
        boolean isSearch = providerTestService.isSearchDimension(provider);
        ProviderState state = isSearch ? current.searchState() : current.llmState();

        boolean testable = state == ProviderState.CONFIGURED
                        || state == ProviderState.UNREACHABLE
                        || state == ProviderState.UNAVAILABLE;
        if (testable) {
            providerTestService.test(provider);
        }
        return providerStatusService.getStatus();
    }
}
