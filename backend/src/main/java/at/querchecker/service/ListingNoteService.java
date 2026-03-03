package at.querchecker.service;

import at.querchecker.dto.QuercheckerNoteDto;
import at.querchecker.entity.ListingNote;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.ListingNoteRepository;
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
public class ListingNoteService {

    private final ListingNoteRepository listingNoteRepository;
    private final WhListingRepository whListingRepository;

    @Transactional(readOnly = true)
    public List<QuercheckerNoteDto> findByListingId(Long whListingId) {
        return listingNoteRepository.findByWhListingIdId(whListingId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public QuercheckerNoteDto create(Long whListingId, QuercheckerNoteDto dto) {
        WhListing listing = whListingRepository.findById(whListingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "WhListing not found: " + whListingId));

        ListingNote note = ListingNote.builder()
                .whListingId(listing)
                .content(dto.getContent())
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(listingNoteRepository.save(note));
    }

    @Transactional
    public QuercheckerNoteDto update(Long noteId, QuercheckerNoteDto dto) {
        ListingNote note = listingNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Note not found: " + noteId));

        note.setContent(dto.getContent());
        note.setUpdatedAt(LocalDateTime.now());

        return toDto(listingNoteRepository.save(note));
    }

    @Transactional
    public void delete(Long noteId) {
        listingNoteRepository.deleteById(noteId);
    }

    private QuercheckerNoteDto toDto(ListingNote entity) {
        return QuercheckerNoteDto.builder()
                .id(entity.getId())
                .whListingId(entity.getWhListingId().getId())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
