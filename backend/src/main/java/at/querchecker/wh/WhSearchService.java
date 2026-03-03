package at.querchecker.wh;

import at.querchecker.dto.QuercheckerListingDto;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.wh.api.WhApiResponse;
import at.querchecker.wh.api.WhApiResponse.Advert;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhSearchService {

    private static final String WH_HOST = "www.willhaben.at";
    private static final String WH_SEARCH_PATH =
        "/webapi/iad/search/atz/seo/kaufen-und-verkaufen/marktplatz";
    private static final String WH_LISTING_BASE =
        "https://www.willhaben.at/iad/";

    private final WhApiClient whApiClient;
    private final WhListingRepository whListingRepository;

    /**
     * Sucht auf Willhaben nach dem angegebenen Keyword, upsertet alle Ergebnisse
     * in die DB (Anker: whId) und gibt sie als DTOs zurück.
     *
     * @param keyword       Suchbegriff, z.B. "rtx 4070"
     * @param rows          Anzahl der Ergebnisse (default 30, Willhaben-seitig max ~100)
     * @param priceFrom     Mindestpreis in € (optional)
     * @param priceTo       Höchstpreis in € (optional)
     * @param attributeTree Willhaben ATTRIBUTE_TREE-ID für Kategorie-Filter (optional)
     * @param areaId        Willhaben areaId für Standort-Filter (optional)
     */
    @Transactional
    public List<QuercheckerListingDto> search(
        String keyword,
        int rows,
        Integer priceFrom,
        Integer priceTo,
        Integer attributeTree,
        Integer areaId
    ) {
        URI uri;
        try {
            StringBuilder query = new StringBuilder();
            query.append("keyword=").append(keyword);
            query.append("&rows=").append(rows);
            query.append("&page=1");
            if (priceFrom != null)     query.append("&PRICE_FROM=").append(priceFrom);
            if (priceTo != null)       query.append("&PRICE_TO=").append(priceTo);
            if (attributeTree != null) query.append("&ATTRIBUTE_TREE=").append(attributeTree);
            if (areaId != null)        query.append("&areaId=").append(areaId);

            uri = new URI("https", WH_HOST, WH_SEARCH_PATH, query.toString(), null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültiger Suchbegriff: " + keyword, e);
        }

        WhApiResponse.Root body = whApiClient.get(uri, WhApiResponse.Root.class).getBody();

        if (body == null
                || body.getAdvertSummaryList() == null
                || body.getAdvertSummaryList().getAdvertSummary() == null) {
            return List.of();
        }

        return body.getAdvertSummaryList().getAdvertSummary()
            .stream()
            .map(this::upsertAndMap)
            .toList();
    }

    private QuercheckerListingDto upsertAndMap(Advert advert) {
        WhListing listing = whListingRepository
            .findByWhId(advert.getId())
            .orElse(WhListing.builder().whId(advert.getId()).build());

        listing.setTitle(advert.getAttribute("HEADING"));
        listing.setDescription(advert.getDescription());
        listing.setPrice(parsePrice(advert.getAttribute("PRICE")));
        listing.setLocation(advert.getAttribute("LOCATION"));
        listing.setUrl(buildListingUrl(advert));
        listing.setFetchedAt(LocalDateTime.now());
        // PUBLISHED_String liefert ISO-Datum; PUBLISHED liefert Epoch-Millisekunden
        listing.setListedAt(parseDateTime(advert.getAttribute("PUBLISHED_String")));

        return toDto(whListingRepository.save(listing));
    }

    /**
     * Baut die nutzerseitige Willhaben-URL aus dem SEO_URL-Attribut zusammen.
     */
    private static String buildListingUrl(Advert advert) {
        String seoUrl = advert.getAttribute("SEO_URL");
        if (seoUrl == null) return null;
        return WH_LISTING_BASE + seoUrl;
    }

    private QuercheckerListingDto toDto(WhListing entity) {
        return QuercheckerListingDto.builder()
            .id(entity.getId())
            .whId(entity.getWhId())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .price(entity.getPrice())
            .location(entity.getLocation())
            .url(entity.getUrl())
            .listedAt(entity.getListedAt())
            .fetchedAt(entity.getFetchedAt())
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
