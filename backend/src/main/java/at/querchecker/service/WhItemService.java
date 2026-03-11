package at.querchecker.service;

import at.querchecker.dto.WhListingDetailDto;
import at.querchecker.dto.WhPreviewDto;
import at.querchecker.entity.WhItem;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.wh.WhConstants;
import at.querchecker.wh.WhSearchService;
import at.querchecker.wh.api.WhApiResponse;
import at.querchecker.wh.api.WhApiResponse.Advert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhItemService {

    private final WhItemRepository whItemRepository;
    private final WhListingRepository whListingRepository;
    private final WhSearchService whSearchService;

    @Transactional
    public WhListingDetailDto openDetail(Long whListingId) {
        WhItem item = getOrCreate(whListingId);
        LocalDateTime now = LocalDateTime.now();
        if (item.getLastViewedAt() == null ||
                java.time.Duration.between(item.getLastViewedAt(), now).getSeconds() > 60) {
            item.setViewCount(item.getViewCount() + 1);
            item.setLastViewedAt(now);
            item.setUpdatedAt(now);
            whItemRepository.save(item);
        }

        WhListing listing = item.getWhListing();
        String description = listing.getDescription();
        List<WhPreviewDto> previews = listing.getImagePaths().stream()
                .map(p -> WhPreviewDto.builder()
                        .thumbUrl(WhConstants.WH_IMAGE_BASE + p + "_thumb.jpg")
                        .fullUrl(WhConstants.WH_IMAGE_BASE + p + ".jpg")
                        .build())
                .collect(Collectors.toList());

        // Fetch fresh description and images from WH (_next/data endpoint)
        Advert advert = whSearchService.fetchListingDetail(listing.getUrl());
        if (advert != null) {
            // DESCRIPTION attribute has the full untruncated text (BODY_DYN in search is truncated)
            String freshDesc = advert.getAttribute("DESCRIPTION");
            if (freshDesc == null || freshDesc.isBlank()) freshDesc = advert.getAttribute("BODY_DYN");
            if (freshDesc == null || freshDesc.isBlank()) freshDesc = advert.getDescription();
            if (freshDesc != null && !freshDesc.isBlank()) {
                description = freshDesc;
                listing.setDescription(description);
            }
            if (advert.getAdvertImageList() != null
                    && advert.getAdvertImageList().getAdvertImage() != null
                    && !advert.getAdvertImageList().getAdvertImage().isEmpty()) {
                List<WhApiResponse.AdvertImage> images = advert.getAdvertImageList().getAdvertImage();
                previews = images.stream()
                        .filter(img -> img.getReferenceImageUrl() != null && img.getThumbnailImageUrl() != null)
                        .map(img -> WhPreviewDto.builder()
                                .thumbUrl(img.getThumbnailImageUrl())
                                .fullUrl(img.getReferenceImageUrl())
                                .build())
                        .collect(Collectors.toList());
                // Update stored image paths from mainImageUrl (strip prefix + .jpg → stem)
                List<String> freshPaths = images.stream()
                        .filter(img -> img.getMainImageUrl() != null
                                && img.getMainImageUrl().startsWith(WhConstants.WH_IMAGE_BASE)
                                && img.getMainImageUrl().endsWith(".jpg"))
                        .map(img -> img.getMainImageUrl()
                                .substring(WhConstants.WH_IMAGE_BASE.length(),
                                           img.getMainImageUrl().length() - 4))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (!freshPaths.isEmpty()) {
                    listing.getImagePaths().clear();
                    listing.getImagePaths().addAll(freshPaths);
                    whListingRepository.save(listing);
                }
            }
        }

        return toDto(item, description, previews);
    }

    @Transactional
    public WhListingDetailDto updateRating(Long whListingId, String rating) {
        WhItem item = getOrCreate(whListingId);
        item.setRating(rating);
        item.setUpdatedAt(LocalDateTime.now());
        return toDto(whItemRepository.save(item));
    }

    @Transactional
    public WhListingDetailDto updateNote(Long whListingId, String note) {
        WhItem item = getOrCreate(whListingId);
        item.setNote(note);
        item.setUpdatedAt(LocalDateTime.now());
        return toDto(whItemRepository.save(item));
    }

    @Transactional
    public WhListingDetailDto updateInterest(Long whListingId, String level) {
        WhItem item = getOrCreate(whListingId);
        item.setInterestLevel(level);
        item.setUpdatedAt(LocalDateTime.now());
        return toDto(whItemRepository.save(item));
    }

    @Transactional
    public WhListingDetailDto updateTags(Long whListingId, List<String> tags) {
        WhItem item = getOrCreate(whListingId);
        item.getTags().clear();
        item.getTags().addAll(tags);
        item.setUpdatedAt(LocalDateTime.now());
        return toDto(whItemRepository.save(item));
    }

    private WhItem getOrCreate(Long whListingId) {
        return whItemRepository.findByWhListingId(whListingId)
                .orElseGet(() -> {
                    WhListing listing = whListingRepository.findById(whListingId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "WhListing not found: " + whListingId));
                    return whItemRepository.save(WhItem.builder()
                            .whListing(listing)
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private WhListingDetailDto toDto(WhItem entity, String description, List<WhPreviewDto> previews) {
        return WhListingDetailDto.builder()
                .id(entity.getId())
                .whListingId(entity.getWhListing().getId())
                .description(description)
                .previews(previews)
                .note(entity.getNote())
                .viewCount(entity.getViewCount())
                .lastViewedAt(entity.getLastViewedAt())
                .rating(entity.getRating())
                .interestLevel(entity.getInterestLevel())
                .tags(entity.getTags())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private WhListingDetailDto toDto(WhItem entity) {
        return toDto(entity, null, null);
    }
}
