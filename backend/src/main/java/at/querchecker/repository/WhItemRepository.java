package at.querchecker.repository;

import at.querchecker.entity.WhItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhItemRepository extends JpaRepository<WhItem, Long> {

    Optional<WhItem> findByWhListingId(Long whListingId);

    @Query("SELECT d.whListing.id AS listingId, d.note AS note, d.viewCount AS viewCount, d.lastViewedAt AS lastViewedAt, d.rating AS rating, d.interestLevel AS interestLevel FROM WhItem d")
    List<WhItemSummary> findAllSummaries();

    @Query("SELECT d.whListing.id FROM WhItem d WHERE d.rating = :rating AND d.createdAt < :cutoff")
    List<Long> findListingIdsByRatingAndCreatedBefore(@Param("rating") String rating, @Param("cutoff") LocalDateTime cutoff);

    List<WhItem> findAllByWhListingIdIn(List<Long> whListingIds);

    interface WhItemSummary {
        Long getListingId();
        String getNote();
        int getViewCount();
        LocalDateTime getLastViewedAt();
        String getRating();
        String getInterestLevel();
    }
}
