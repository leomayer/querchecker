package at.querchecker.service;

import at.querchecker.dto.QuercheckerListingDto;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingDetailRepository;
import at.querchecker.repository.WhListingDetailRepository.WhListingDetailSummary;
import at.querchecker.repository.WhListingRepository;
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
    private final WhListingDetailRepository whListingDetailRepository;

    @Transactional(readOnly = true)
    public List<QuercheckerListingDto> findAll(String ratingFilter) {
        List<WhListing> listings = whListingRepository.findAll();

        Map<Long, WhListingDetailSummary> detailMap = whListingDetailRepository.findAllSummaries()
                .stream()
                .collect(Collectors.toMap(WhListingDetailSummary::getListingId, s -> s));

        var stream = listings.stream().map(e -> toDto(e, detailMap.get(e.getId())));

        return switch (ratingFilter) {
            case "UP" -> stream.filter(d -> "UP".equals(d.getRating())).toList();
            case "UP_NULL" -> stream.filter(d -> d.getRating() == null || "UP".equals(d.getRating())).toList();
            case "DOWN" -> stream.filter(d -> "DOWN".equals(d.getRating())).toList();
            default -> stream.toList(); // "ALL"
        };
    }

    @Transactional(readOnly = true)
    public Optional<QuercheckerListingDto> findById(Long id) {
        return whListingRepository.findById(id).map(e -> toDto(e, null));
    }

    @Transactional
    public QuercheckerListingDto save(QuercheckerListingDto dto) {
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
        List<Long> listingIds = whListingDetailRepository.findListingIdsByRatingAndCreatedBefore(rating, cutoff);
        if (listingIds.isEmpty()) return 0;
        // Delete via entity lifecycle so @ElementCollection (tags) cascades correctly
        whListingDetailRepository.deleteAllById(
                whListingDetailRepository.findAllByWhListingIdIn(listingIds).stream()
                        .map(d -> d.getId()).toList()
        );
        whListingRepository.deleteAllById(listingIds);
        return listingIds.size();
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
                .interestLevel(detail != null ? detail.getInterestLevel() : null)
                .build();
    }
}
