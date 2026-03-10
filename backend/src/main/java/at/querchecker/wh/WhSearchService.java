package at.querchecker.wh;

import at.querchecker.dto.QuercheckerListingDto;
import at.querchecker.dto.WhSearchResultDto;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingDetailRepository;
import at.querchecker.repository.WhListingDetailRepository.WhListingDetailSummary;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.wh.api.WhApiResponse;
import at.querchecker.wh.api.WhApiResponse.Advert;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final WhListingDetailRepository whListingDetailRepository;

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
     * @param paylivery     Nur Paylivery-Angebote (optional)
     */
    @Transactional
    public WhSearchResultDto search(
        String keyword,
        int rows,
        Integer priceFrom,
        Integer priceTo,
        Integer attributeTree,
        Integer areaId,
        Boolean paylivery
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
            if (Boolean.TRUE.equals(paylivery)) query.append("&paylivery=true");

            uri = new URI("https", WH_HOST, WH_SEARCH_PATH, query.toString(), null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültiger Suchbegriff: " + keyword, e);
        }

        WhApiResponse.Root body = whApiClient.get(uri, WhApiResponse.Root.class).getBody();

        if (body == null
                || body.getAdvertSummaryList() == null
                || body.getAdvertSummaryList().getAdvertSummary() == null) {
            return WhSearchResultDto.builder().totalCount(0).listings(List.of()).build();
        }

        // Deduplicate adverts by whId (Willhaben returns promoted listings twice).
        // LinkedHashMap preserves insertion order so the result list stays stable.
        List<Advert> adverts = body.getAdvertSummaryList().getAdvertSummary()
            .stream()
            .collect(Collectors.toMap(
                Advert::getId,
                a -> a,
                (existing, duplicate) -> existing,
                LinkedHashMap::new))
            .values().stream().toList();

        // Batch-load all existing listings in a single query, then save all at once.
        Map<String, WhListing> existingByWhId = whListingRepository
            .findAllByWhIdIn(adverts.stream().map(Advert::getId).toList())
            .stream()
            .collect(Collectors.toMap(WhListing::getWhId, l -> l));

        List<WhListing> savedListings = whListingRepository.saveAll(
            adverts.stream().map(advert -> applyAdvert(
                existingByWhId.getOrDefault(advert.getId(),
                    WhListing.builder().whId(advert.getId()).build()),
                advert))
            .toList());

        List<Long> ids = savedListings.stream().map(WhListing::getId).toList();
        Map<Long, WhListingDetailSummary> detailMap = whListingDetailRepository.findAllSummaries()
            .stream()
            .filter(s -> ids.contains(s.getListingId()))
            .collect(Collectors.toMap(WhListingDetailSummary::getListingId, s -> s));

        List<QuercheckerListingDto> listings = savedListings.stream()
            .map(l -> toDto(l, detailMap.get(l.getId())))
            .toList();

        return WhSearchResultDto.builder()
            .totalCount(body.getRowsFound())
            .listings(listings)
            .build();
    }

    private WhListing applyAdvert(WhListing listing, Advert advert) {
        listing.setTitle(advert.getAttribute("HEADING"));
        listing.setDescription(advert.getAttribute("BODY_DYN"));
        listing.setPrice(parsePrice(advert.getAttribute("PRICE")));
        listing.setLocation(advert.getAttribute("LOCATION"));
        listing.setUrl(buildListingUrl(advert));
        listing.setFetchedAt(LocalDateTime.now());
        // PUBLISHED_String liefert ISO-Datum; PUBLISHED liefert Epoch-Millisekunden
        listing.setListedAt(parseDateTime(advert.getAttribute("PUBLISHED_String")));
        listing.setThumbnailUrl(buildThumbnailUrl(advert));
        return listing;
    }

    private static final String WH_IMAGE_BASE = "https://cache.willhaben.at/mmo/";

    /**
     * Baut die Thumbnail-URL aus dem MMO-Attribut zusammen (primär).
     * Falls nicht vorhanden, Fallback auf advertImageList.
     * Muster: "https://cache.willhaben.at/mmo/{path}_thumb.jpg"
     */
    private static String buildThumbnailUrl(Advert advert) {
        String mmo = advert.getAttribute("MMO");
        if (mmo != null && mmo.endsWith(".jpg")) {
            return WH_IMAGE_BASE + mmo.substring(0, mmo.length() - 4) + "_thumb.jpg";
        }
        return advert.getThumbnailUrl(); // fallback auf advertImageList
    }

    /**
     * Baut die nutzerseitige Willhaben-URL aus dem SEO_URL-Attribut zusammen.
     */
    private static String buildListingUrl(Advert advert) {
        String seoUrl = advert.getAttribute("SEO_URL");
        if (seoUrl == null) return null;
        return WH_LISTING_BASE + seoUrl;
    }

    private QuercheckerListingDto toDto(WhListing entity, WhListingDetailSummary detail) {
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
            .thumbnailUrl(entity.getThumbnailUrl())
            .hasNote(detail != null && detail.getNote() != null && !detail.getNote().isBlank())
            .viewCount(detail != null ? detail.getViewCount() : 0)
            .lastViewedAt(detail != null ? detail.getLastViewedAt() : null)
            .rating(detail != null ? detail.getRating() : null)
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
