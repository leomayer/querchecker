package at.querchecker.service;

import at.querchecker.dto.QuercheckerListingDto;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhListingService {

    private final WhListingRepository whListingRepository;

    @Transactional(readOnly = true)
    public List<QuercheckerListingDto> findAll() {
        return whListingRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<QuercheckerListingDto> findById(Long id) {
        return whListingRepository.findById(id).map(this::toDto);
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

        return toDto(whListingRepository.save(entity));
    }

    @Transactional
    public void deleteById(Long id) {
        whListingRepository.deleteById(id);
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
}
