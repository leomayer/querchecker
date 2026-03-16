package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ItemSource;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.ItemTextRepository;
import at.querchecker.entity.WhListing;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ItemTextService {

    private final ItemTextRepository repo;

    /**
     * Strategy: bei Inhaltsänderung neuer Record – kein Update.
     * Alter Record + alte DlExtractionRuns bleiben auf DONE erhalten bis TTL-Cleanup greift.
     */
    public ItemText findOrCreateOrUpdate(WhListing listing) {
        // HTML strippen via Jsoup (robuster als HtmlUtils – behandelt Entities korrekt)
        String cleanTitle = stripHtml(listing.getTitle());
        String cleanDescription = stripHtml(listing.getDescription());
        String newHash = sha256(cleanTitle + cleanDescription);

        // Aktuellsten Record holen (1:n – mehrere Records pro WhListing möglich)
        return repo.findFirstByWhListingOrderByFetchedAtDesc(listing)
            .filter(existing -> newHash.equals(existing.getContentHash()))
            .orElseGet(() -> repo.save(ItemText.builder()
                .whListing(listing)
                .source(ItemSource.WILLHABEN)
                .title(cleanTitle)
                .description(cleanDescription)
                .contentHash(newHash)
                .fetchedAt(LocalDateTime.now())
                .build()));
        // Neuer Record → DlOrchestrationService.scheduleExtraction() in upsertListing()
        // wird mit neuem ItemText aufgerufen → neue INIT-Runs automatisch
    }

    private String stripHtml(String text) {
        if (text == null) return "";
        return Jsoup.parse(text).text();  // Jsoup: transitiv via Spring Boot
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
