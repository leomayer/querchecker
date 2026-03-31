package at.querchecker.service;

import at.querchecker.dto.ListingRefreshedPayload;
import at.querchecker.dto.WhCategoryDto;
import at.querchecker.dto.WhPreviewDto;
import at.querchecker.entity.WhItem;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhCategoryRepository;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.sse.SseHub;
import at.querchecker.willHaben.WhConstants;
import at.querchecker.willHaben.WhSearchService;
import at.querchecker.willHaben.api.WhApiResponse;
import at.querchecker.willHaben.api.WhApiResponse.Advert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches fresh listing data (description, images, category) from Willhaben
 * asynchronously after openDetail() has already returned cached data.
 * On completion, broadcasts a `listing-refreshed` SSE event so the frontend
 * can update the detail panel without a page reload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhListingRefreshService {

    private final WhSearchService whSearchService;
    private final WhListingRepository whListingRepository;
    private final WhItemRepository whItemRepository;
    private final WhCategoryRepository whCategoryRepository;
    private final SseHub sseHub;

    @Async
    @Transactional
    public void refreshAsync(Long listingId, Long whItemId) {
        WhListing listing = whListingRepository.findById(listingId).orElse(null);
        if (listing == null) return;

        Advert advert = whSearchService.fetchListingDetail(listing.getUrl());
        if (advert == null) return;

        boolean changed = false;
        String description = listing.getDescription();
        List<WhPreviewDto> previews = null;

        // Update description (DESCRIPTION is untruncated; BODY_DYN from search is truncated)
        String freshDesc = advert.getAttribute("DESCRIPTION");
        if (freshDesc == null || freshDesc.isBlank()) freshDesc = advert.getAttribute("BODY_DYN");
        if (freshDesc == null || freshDesc.isBlank()) freshDesc = advert.getDescription();
        if (freshDesc != null && !freshDesc.isBlank() && !freshDesc.equals(description)) {
            listing.setDescription(freshDesc);
            description = freshDesc;
            changed = true;
        }

        // Update image paths
        if (advert.getAdvertImageList() != null
                && advert.getAdvertImageList().getAdvertImage() != null
                && !advert.getAdvertImageList().getAdvertImage().isEmpty()) {
            List<WhApiResponse.AdvertImage> images = advert.getAdvertImageList().getAdvertImage();
            List<String> freshPaths = images.stream()
                    .filter(img -> img.getMainImageUrl() != null
                            && img.getMainImageUrl().startsWith(WhConstants.WH_IMAGE_BASE)
                            && img.getMainImageUrl().endsWith(".jpg"))
                    .map(img -> img.getMainImageUrl()
                            .substring(WhConstants.WH_IMAGE_BASE.length(),
                                       img.getMainImageUrl().length() - 4))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!freshPaths.isEmpty() && !freshPaths.equals(listing.getImagePaths())) {
                listing.getImagePaths().clear();
                listing.getImagePaths().addAll(freshPaths);
                previews = images.stream()
                        .filter(img -> img.getReferenceImageUrl() != null && img.getThumbnailImageUrl() != null)
                        .map(img -> WhPreviewDto.builder()
                                .thumbUrl(normalizeThumbnailUrl(img.getThumbnailImageUrl()))
                                .fullUrl(img.getReferenceImageUrl())
                                .build())
                        .collect(Collectors.toList());
                changed = true;
            }
        }

        // Lazy category assignment
        List<WhCategoryDto> categoryPath = null;
        if (listing.getWhCategory() == null) {
            Integer catWhId = WhSearchService.parseDeepestCategoryWhId(advert);
            if (catWhId != null) {
                var cat = whCategoryRepository.findByWhId(catWhId).orElse(null);
                if (cat != null) {
                    listing.setWhCategory(cat);
                    categoryPath = buildCategoryPath(listing);
                    changed = true;
                    log.debug("Lazy category assigned async: listingWhId={} → {}", listing.getWhId(), cat.getName());
                }
            }
        }

        if (!changed) return;

        whListingRepository.save(listing);

        // Build previews from updated paths if not already set from fresh image list
        if (previews == null) {
            previews = listing.getImagePaths().stream()
                    .map(p -> WhPreviewDto.builder()
                            .thumbUrl(WhConstants.WH_IMAGE_BASE + p + "_thumb.jpg")
                            .fullUrl(WhConstants.WH_IMAGE_BASE + p + ".jpg")
                            .build())
                    .collect(Collectors.toList());
        }
        if (categoryPath == null) {
            categoryPath = buildCategoryPath(listing);
        }

        sseHub.broadcast("listing-refreshed", ListingRefreshedPayload.builder()
                .whItemId(whItemId)
                .description(description)
                .previews(previews)
                .categoryPath(categoryPath)
                .build());

        log.debug("listing-refreshed broadcast: whItemId={}, listingId={}", whItemId, listingId);
    }

    /**
     * Normalizes thumbnail URLs from Willhaben API by removing variant suffixes.
     * Example: "…/248_449701018_hoved_thumb.jpg" → "…/248_449701018_thumb.jpg"
     */
    private static String normalizeThumbnailUrl(String url) {
        if (url == null) return null;
        // Remove variant suffixes like "_hoved_" that appear before "_thumb"
        return url.replaceAll("(_[a-z]+)?_thumb", "_thumb");
    }

    private List<WhCategoryDto> buildCategoryPath(WhListing listing) {
        var category = listing.getWhCategory();
        if (category == null) return List.of();
        var path = new ArrayList<WhCategoryDto>();
        var current = category;
        while (current != null) {
            path.add(0, WhCategoryDto.builder()
                    .id(current.getId())
                    .whId(current.getWhId())
                    .name(current.getName())
                    .level(current.getLevel())
                    .build());
            current = current.getParent();
        }
        return path;
    }
}
