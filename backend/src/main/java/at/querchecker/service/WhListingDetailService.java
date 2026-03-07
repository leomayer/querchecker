package at.querchecker.service;

import at.querchecker.dto.WhListingDetailDto;
import at.querchecker.entity.WhListing;
import at.querchecker.entity.WhListingDetail;
import at.querchecker.repository.WhListingDetailRepository;
import at.querchecker.repository.WhListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WhListingDetailService {

    private final WhListingDetailRepository whListingDetailRepository;
    private final WhListingRepository whListingRepository;

    @Transactional(readOnly = true)
    public WhListingDetailDto getDetail(Long whListingId) {
        return whListingDetailRepository.findByWhListingId(whListingId)
                .map(this::toDto)
                .orElse(emptyDto(whListingId));
    }

    @Transactional
    public void recordView(Long whListingId) {
        WhListingDetail detail = getOrCreate(whListingId);
        detail.setViewCount(detail.getViewCount() + 1);
        detail.setLastViewedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        whListingDetailRepository.save(detail);
    }

    @Transactional
    public WhListingDetailDto updateRating(Long whListingId, String rating) {
        WhListingDetail detail = getOrCreate(whListingId);
        detail.setRating(rating);
        detail.setUpdatedAt(LocalDateTime.now());
        return toDto(whListingDetailRepository.save(detail));
    }

    @Transactional
    public WhListingDetailDto updateNote(Long whListingId, String note) {
        WhListingDetail detail = getOrCreate(whListingId);
        detail.setNote(note);
        detail.setUpdatedAt(LocalDateTime.now());
        return toDto(whListingDetailRepository.save(detail));
    }

    private WhListingDetail getOrCreate(Long whListingId) {
        return whListingDetailRepository.findByWhListingId(whListingId)
                .orElseGet(() -> {
                    WhListing listing = whListingRepository.findById(whListingId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "WhListing not found: " + whListingId));
                    return whListingDetailRepository.save(WhListingDetail.builder()
                            .whListing(listing)
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private WhListingDetailDto toDto(WhListingDetail entity) {
        return WhListingDetailDto.builder()
                .id(entity.getId())
                .whListingId(entity.getWhListing().getId())
                .note(entity.getNote())
                .viewCount(entity.getViewCount())
                .lastViewedAt(entity.getLastViewedAt())
                .rating(entity.getRating())
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
