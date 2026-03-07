package at.querchecker.repository;

import at.querchecker.entity.WhListingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhListingDetailRepository extends JpaRepository<WhListingDetail, Long> {

    Optional<WhListingDetail> findByWhListingId(Long whListingId);

    @Query("SELECT d.whListing.id AS listingId, d.note AS note, d.viewCount AS viewCount, d.lastViewedAt AS lastViewedAt, d.rating AS rating FROM WhListingDetail d")
    List<WhListingDetailSummary> findAllSummaries();

    interface WhListingDetailSummary {
        Long getListingId();
        String getNote();
        int getViewCount();
        LocalDateTime getLastViewedAt();
        String getRating();
    }
}
