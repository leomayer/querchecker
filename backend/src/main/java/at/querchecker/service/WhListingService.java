package at.querchecker.service;

import at.querchecker.dto.WhItemDto;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhItemRepository.WhItemSummary;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.wh.WhConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WhListingService {

    private final WhListingRepository whListingRepository;
    private final WhItemRepository whItemRepository;

    @Transactional(readOnly = true)
    public List<WhItemDto> findAll(String ratingFilter) {
        List<WhListing> listings = whListingRepository.findAll();

        Map<Long, WhItemSummary> detailMap = whItemRepository.findAllSummaries()
                .stream()
                .collect(Collectors.toMap(WhItemSummary::getListingId, s -> s));

        var stream = listings.stream().map(e -> toDto(e, detailMap.get(e.getId())));

        return switch (ratingFilter) {
            case "UP" -> stream.filter(d -> "UP".equals(d.getRating())).toList();
            case "UP_NULL" -> stream.filter(d -> d.getRating() == null || "UP".equals(d.getRating())).toList();
            case "DOWN" -> stream.filter(d -> "DOWN".equals(d.getRating())).toList();
            default -> stream.toList(); // "ALL"
        };
    }

    @Transactional(readOnly = true)
    public Optional<WhItemDto> findById(Long id) {
        return whListingRepository.findById(id).map(e -> toDto(e, null));
    }

    @Transactional
    public WhItemDto save(WhItemDto dto) {
        WhListing entity = whListingRepository.findByWhId(dto.getWhId())
                .orElse(WhListing.builder().build());

        entity.setWhId(dto.getWhId());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setPrice(dto.getPrice());
        entity.setLocation(dto.getLocation());
        entity.setUrl(dto.getUrl());
        entity.setListedAt(dto.getListedAt());
        entity.setFetchedAt(LocalDateTime.now());

        return toDto(whListingRepository.save(entity), null);
    }

    @Transactional
    public void deleteById(Long id) {
        whListingRepository.deleteById(id);
    }

    @Transactional
    public int cleanupByRating(String rating, int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        List<Long> listingIds = whItemRepository.findListingIdsByRatingAndCreatedBefore(rating, cutoff);
        if (listingIds.isEmpty()) return 0;
        whItemRepository.deleteAllById(
                whItemRepository.findAllByWhListingIdIn(listingIds).stream()
                        .map(d -> d.getId()).toList()
        );
        whListingRepository.deleteAllById(listingIds);
        return listingIds.size();
    }

    private WhItemDto toDto(WhListing entity, WhItemSummary detail) {
        String thumbnailPath = entity.getThumbnailUrl();
        String fullThumbnailUrl = thumbnailPath != null
            ? WhConstants.WH_IMAGE_BASE + thumbnailPath + "_thumb.jpg"
            : null;

        String urlPath = entity.getUrl();
        String fullUrl = urlPath != null
            ? WhConstants.WH_LISTING_BASE + urlPath
            : null;

        List<String> imageUrls = entity.getImagePaths().stream()
            .map(p -> WhConstants.WH_IMAGE_BASE + p + ".jpg")
            .toList();

        return WhItemDto.builder()
                .id(entity.getId())
                .whId(entity.getWhId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .location(entity.getLocation())
                .url(fullUrl)
                .listedAt(entity.getListedAt())
                .fetchedAt(entity.getFetchedAt())
                .thumbnailUrl(fullThumbnailUrl)
                .imageUrls(imageUrls)
                .hasNote(detail != null && detail.getNote() != null && !detail.getNote().isBlank())
                .viewCount(detail != null ? detail.getViewCount() : 0)
                .lastViewedAt(detail != null ? detail.getLastViewedAt() : null)
                .rating(detail != null ? detail.getRating() : null)
                .interestLevel(detail != null ? detail.getInterestLevel() : null)
                .build();
    }
}
