package at.querchecker.research.repository;

import at.querchecker.research.entity.ListingLookupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ListingLookupHistoryRepository extends JpaRepository<ListingLookupHistory, Long> {

    List<ListingLookupHistory> findByListingIdOrderByCreatedAtDesc(Long listingId);

    long countByListingId(Long listingId);

    @Transactional
    void deleteByListingIdAndLookupTerm(Long listingId, String lookupTerm);
}
