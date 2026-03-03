package at.querchecker.repository;

import at.querchecker.entity.ListingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingNoteRepository extends JpaRepository<ListingNote, Long> {

    List<ListingNote> findByWhListingIdId(Long whListingId);
}
