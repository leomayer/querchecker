package at.querchecker.willHaben;

import at.querchecker.dto.WhCategoryDto;
import at.querchecker.dto.WhItemDto;
import at.querchecker.dto.WhSearchResultDto;
import at.querchecker.entity.WhCategory;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhItemRepository.WhItemSummary;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.willHaben.api.WhApiResponse;
import at.querchecker.willHaben.api.WhApiResponse.Advert;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhSearchService {

    private static final String WH_IMAGE_BASE   = WhConstants.WH_IMAGE_BASE;
    private static final String WH_LISTING_BASE = WhConstants.WH_LISTING_BASE;

    private static final String WH_HOST = "www.willhaben.at";
    private static final String WH_SEARCH_PATH =
        "/webapi/iad/search/atz/seo/kaufen-und-verkaufen/marktplatz";

    // language=SQL
    private static final String UPSERT_SQL = """
        INSERT INTO wh_listing
            (wh_id, title, description, price, location, url, listed_at, fetched_at, thumbnail_url, paylivery)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (wh_id) DO UPDATE SET
            title          = EXCLUDED.title,
            description    = EXCLUDED.description,
            price          = EXCLUDED.price,
            location       = EXCLUDED.location,
            url            = EXCLUDED.url,
            listed_at      = EXCLUDED.listed_at,
            fetched_at     = EXCLUDED.fetched_at,
            thumbnail_url  = EXCLUDED.thumbnail_url,
            paylivery      = EXCLUDED.paylivery
        """;

    private final WhApiClient whApiClient;
    private final WhListingRepository whListingRepository;
    private final WhItemRepository whItemRepository;
    private final WhCategoryRepository whCategoryRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Sucht auf Willhaben nach dem angegebenen Keyword, upsertet alle Ergebnisse
     * in die DB (Anker: whId) und gibt sie als DTOs zurück.
     */
    public WhSearchResultDto search(
        String keyword,
        int rows,
        Integer priceFrom,
        Integer priceTo,
        Integer attributeTree,
        Integer areaId,
        Boolean paylivery
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl("https://" + WH_HOST + WH_SEARCH_PATH)
            .queryParam("rows", rows)
            .queryParam("page", 1);
        if (keyword != null && !keyword.isBlank()) builder.queryParam("keyword", keyword);
        if (priceFrom != null)     builder.queryParam("PRICE_FROM", priceFrom);
        if (priceTo != null)       builder.queryParam("PRICE_TO", priceTo);
        if (attributeTree != null) builder.queryParam("ATTRIBUTE_TREE", attributeTree);
        if (areaId != null)        builder.queryParam("areaId", areaId);
        if (Boolean.TRUE.equals(paylivery)) builder.queryParam("paylivery", "true");
        URI uri = builder.encode().build().toUri();

        WhApiResponse.Root body = whApiClient.get(uri, WhApiResponse.Root.class).getBody();

        if (body == null
                || body.getAdvertSummaryList() == null
                || body.getAdvertSummaryList().getAdvertSummary() == null) {
            return WhSearchResultDto.builder().totalCount(0).listings(List.of()).build();
        }

        List<Advert> adverts = body.getAdvertSummaryList().getAdvertSummary()
            .stream()
            .collect(Collectors.toMap(
                Advert::getId,
                a -> a,
                (existing, duplicate) -> existing,
                LinkedHashMap::new))
            .values().stream().toList();

        return persistAndBuildResult(adverts, body.getRowsFound());
    }

    @Transactional
    protected WhSearchResultDto persistAndBuildResult(List<Advert> adverts, Integer totalCount) {
        List<WhListing> toUpsert = adverts.stream()
            .map(a -> applyAdvert(WhListing.builder().whId(a.getId()).build(), a))
            .toList();

        // Upsert main listing data via raw SQL (atomic, race-safe)
        for (WhListing l : toUpsert) {
            jdbcTemplate.update(UPSERT_SQL,
                l.getWhId(), l.getTitle(), l.getDescription(),
                l.getPrice(), l.getLocation(), l.getUrl(),
                l.getListedAt(), l.getFetchedAt(), l.getThumbnailUrl(), l.isPaylivery());
        }

        // Re-fetch to get DB-assigned IDs
        List<String> whIds = adverts.stream().map(Advert::getId).toList();
        List<WhListing> savedListings = whListingRepository.findAllByWhIdIn(whIds);

        // Build maps from in-memory adverts: whId → imagePaths, whId → rootCategoryWhId
        Map<String, List<String>> imagePathsByWhId = toUpsert.stream()
            .collect(Collectors.toMap(WhListing::getWhId, WhListing::getImagePaths));
        Map<String, Integer> rootCategoryByWhId = new java.util.HashMap<>();
        adverts.forEach(a -> {
            Integer catId = parseDeepestCategoryWhId(a);
            log.debug("Category parse: whId={} → deepestCatId={}", a.getId(), catId);
            if (catId != null) rootCategoryByWhId.put(a.getId(), catId);
        });
        log.debug("Category map for {} adverts: {}", adverts.size(), rootCategoryByWhId);

        // Listings that still need a category assigned (first time seen)
        List<WhListing> uncategorized = savedListings.stream()
            .filter(l -> l.getWhCategory() == null && rootCategoryByWhId.containsKey(l.getWhId()))
            .toList();

        if (!uncategorized.isEmpty()) {
            // Batch-load only the required categories in one query
            List<Integer> neededCatIds = uncategorized.stream()
                .map(l -> rootCategoryByWhId.get(l.getWhId()))
                .distinct().toList();
            Map<Integer, WhCategory> categoryMap = whCategoryRepository
                .findByWhIdIn(neededCatIds)
                .stream()
                .collect(Collectors.toMap(WhCategory::getWhId, c -> c));
            log.debug("Category batch lookup: {} unique catIds → {} found", neededCatIds.size(), categoryMap.size());

            uncategorized.forEach(l -> {
                WhCategory cat = categoryMap.get(rootCategoryByWhId.get(l.getWhId()));
                if (cat != null) l.setWhCategory(cat);
            });
        }

        // Save image paths via JPA (replaces previous list)
        for (WhListing saved : savedListings) {
            List<String> paths = imagePathsByWhId.getOrDefault(saved.getWhId(), List.of());
            saved.getImagePaths().clear();
            saved.getImagePaths().addAll(paths);
        }
        whListingRepository.saveAll(savedListings);

        List<Long> ids = savedListings.stream().map(WhListing::getId).toList();
        Map<Long, WhItemSummary> detailMap = whItemRepository.findAllSummaries()
            .stream()
            .filter(s -> ids.contains(s.getListingId()))
            .collect(Collectors.toMap(WhItemSummary::getListingId, s -> s));

        List<WhItemDto> listings = savedListings.stream()
            .map(l -> toDto(l, detailMap.get(l.getId())))
            .toList();

        return WhSearchResultDto.builder()
            .totalCount(totalCount)
            .listings(listings)
            .build();
    }

    private WhListing applyAdvert(WhListing listing, Advert advert) {
        listing.setTitle(advert.getAttribute("HEADING"));
        listing.setDescription(advert.getAttribute("BODY_DYN"));
        listing.setPrice(parsePrice(advert.getAttribute("PRICE")));
        listing.setLocation(advert.getAttribute("LOCATION"));
        listing.setUrl(buildListingPath(advert));
        listing.setFetchedAt(LocalDateTime.now());
        listing.setListedAt(parseDateTime(advert.getAttribute("PUBLISHED_String")));
        listing.setThumbnailUrl(buildThumbnailPath(advert));
        listing.setPaylivery("true".equalsIgnoreCase(advert.getAttribute("p2penabled")));
        listing.setImagePaths(buildImagePaths(advert));
        return listing;
    }

    /**
     * Relativer Pfad-Stem des Thumbnails (ohne Prefix und ohne _thumb.jpg).
     * Muster gespeichert: "1234/5678/9012"
     * Rekonstruktion: WH_IMAGE_BASE + stem + "_thumb.jpg"
     */
    private static String buildThumbnailPath(Advert advert) {
        String mmo = advert.getAttribute("MMO");
        if (mmo != null && mmo.endsWith(".jpg")) {
            return mmo.substring(0, mmo.length() - 4);
        }
        // Fallback: advertImageList Thumbnail — strip prefix if present
        String fallback = advert.getThumbnailUrl();
        if (fallback == null) return null;
        return fallback.startsWith(WH_IMAGE_BASE)
            ? stripThumbSuffix(fallback.substring(WH_IMAGE_BASE.length()))
            : fallback;
    }

    /**
     * Relativer SEO-Pfad (ohne https://www.willhaben.at/iad/ Prefix).
     */
    private static String buildListingPath(Advert advert) {
        return advert.getAttribute("SEO_URL");
    }

    /**
     * Alle Bild-Pfad-Stems aus ALL_IMAGE_URLS (ohne Prefix, ohne .jpg).
     */
    private static List<String> buildImagePaths(Advert advert) {
        return advert.getAttributeValues("ALL_IMAGE_URLS").stream()
            .flatMap(v -> Arrays.stream(v.split(";")))
            .filter(v -> v != null && !v.isBlank() && v.endsWith(".jpg"))
            .map(v -> v.substring(0, v.length() - 4))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<WhCategoryDto> buildCategoryPath(WhCategory category) {
        if (category == null) return List.of();
        List<WhCategoryDto> path = new java.util.ArrayList<>();
        WhCategory current = category;
        while (current != null) {
            path.add(0, WhCategoryDto.builder()
                .whId(current.getWhId())
                .name(current.getName())
                .level(current.getLevel())
                .build());
            current = current.getParent();
        }
        return path;
    }

    private WhItemDto toDto(WhListing entity, WhItemSummary detail) {
        return WhItemDto.builder()
            .id(entity.getId())
            .whId(entity.getWhId())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .price(entity.getPrice())
            .location(entity.getLocation())
            .url(entity.getUrl() != null ? WH_LISTING_BASE + entity.getUrl() : null)
            .listedAt(entity.getListedAt())
            .fetchedAt(entity.getFetchedAt())
            .thumbnailUrl(entity.getThumbnailUrl() != null
                ? WH_IMAGE_BASE + entity.getThumbnailUrl() + "_thumb.jpg" : null)
            .imageUrls(entity.getImagePaths().stream()
                .map(p -> WH_IMAGE_BASE + p + ".jpg")
                .toList())
            .hasNote(detail != null && detail.getNote() != null && !detail.getNote().isBlank())
            .viewCount(detail != null ? detail.getViewCount() : 0)
            .lastViewedAt(detail != null ? detail.getLastViewedAt() : null)
            .rating(detail != null ? detail.getRating() : null)
            .interestLevel(detail != null ? detail.getInterestLevel() : null)
            .paylivery(entity.isPaylivery())
            .categoryPath(buildCategoryPath(entity.getWhCategory()))
            .build();
    }

    /**
     * Ruft ein einzelnes Inserat von der WH-API ab (vollständige Beschreibung, alle Bilder).
     * Gibt null zurück wenn der Abruf fehlschlägt (z.B. Inserat gelöscht).
     */
    public Advert fetchListingDetail(String seoUrl) {
        if (seoUrl == null) return null;
        try {
            log.info("Fetching WH detail for seoUrl={}", seoUrl);
            WhApiResponse.NextDataRoot body =
                whApiClient.getNextData(seoUrl, WhApiResponse.NextDataRoot.class).getBody();
            return body != null && body.getPageProps() != null
                ? body.getPageProps().getAdvertDetails()
                : null;
        } catch (Exception e) {
            log.error("WH detail fetch failed for seoUrl={}", seoUrl, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extrahiert den tiefsten Kategorie-whId aus den WH-Attributen.
     * Primär: category_level_max + category_level_id_{N} (z.B. category_level_id_4=5421).
     * Fallback: categorytreeids (z.B. "5824;5828;5833" → 5833).
     */
    public static Integer parseDeepestCategoryWhId(Advert advert) {
        // Primary: use category_level_max to find the deepest level ID
        String maxStr = advert.getAttribute("category_level_max");
        log.debug("category_level_max={} for whId={}", maxStr, advert.getId());
        if (maxStr != null && !maxStr.isBlank()) {
            try {
                int max = Integer.parseInt(maxStr.trim());
                for (int level = max; level >= 1; level--) {
                    String idStr = advert.getAttribute("category_level_id_" + level);
                    log.debug("  category_level_id_{}={} for whId={}", level, idStr, advert.getId());
                    if (idStr != null && !idStr.isBlank()) {
                        return Integer.parseInt(idStr.trim());
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // Fallback: categorytreeids
        String treeIds = advert.getAttribute("categorytreeids");
        if (treeIds == null || treeIds.isBlank()) return null;
        try {
            String[] parts = treeIds.split(";");
            String last = parts[parts.length - 1].trim();
            return Integer.parseInt(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Entfernt _thumb Suffix falls vorhanden (z.B. "1234/5678_thumb" → "1234/5678"). */
    private static String stripThumbSuffix(String path) {
        if (path != null && path.endsWith("_thumb.jpg")) {
            return path.substring(0, path.length() - 10);
        }
        if (path != null && path.endsWith(".jpg")) {
            return path.substring(0, path.length() - 4);
        }
        return path;
    }

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
