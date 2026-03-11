package at.querchecker.service;

import at.querchecker.dto.WhListingDetailDto;
import at.querchecker.entity.WhItem;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WhItemService {

    private final WhItemRepository whItemRepository;
    private final WhListingRepository whListingRepository;

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
        return toDto(item);
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

    private WhListingDetailDto toDto(WhItem entity) {
        return WhListingDetailDto.builder()
                .id(entity.getId())
                .whListingId(entity.getWhListing().getId())
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

    private WhListingDetailDto emptyDto(Long whListingId) {
        return WhListingDetailDto.builder()
                .whListingId(whListingId)
                .viewCount(0)
                .build();
    }
}
