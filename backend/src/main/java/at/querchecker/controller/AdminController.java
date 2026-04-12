package at.querchecker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-Endpoints für Server-Verwaltung.
 * POST /api/admin/restart — startet den Spring-Kontext neu.
 * SSE-Clients erkennen den Neustart automatisch und reconnecten.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ApplicationContext applicationContext;

    /**
     * Startet die Spring Boot Applikation neu.
     * Sendet 200 OK bevor der Neustart ausgelöst wird, damit der Client die Antwort erhält.
     * SSE-Clients reconnecten automatisch und erhalten ein neues server-hello + provider-status Event.
     */
    @PostMapping("/restart")
    public ResponseEntity<Void> restart() {
        log.info("[Admin] Server-Neustart angefordert");
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(500); // kurze Verzögerung damit HTTP-Antwort gesendet wird
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SpringApplication.exit(applicationContext, () -> 0);
            System.exit(0);
        });
        restartThread.setDaemon(false);
        restartThread.setName("admin-restart");
        restartThread.start();
        return ResponseEntity.ok().build();
    }
}
