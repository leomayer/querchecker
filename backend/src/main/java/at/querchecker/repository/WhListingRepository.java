package at.querchecker.repository;

import at.querchecker.entity.WhListing;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhListingRepository extends JpaRepository<WhListing, Long> {

    // Loads all listings + full category ancestor chain in one SQL (4 self-joins on wh_category).
    // WhCategory.parent is EAGER so the chain is included automatically once whCategory is joined.
    @EntityGraph(attributePaths = {
        "whCategory",
        "whCategory.parent",
        "whCategory.parent.parent",
        "whCategory.parent.parent.parent"
    })
    List<WhListing> findAll();

    Optional<WhListing> findByWhId(String whId);

    List<WhListing> findAllByWhIdIn(Collection<String> whIds);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM WhListing l
        WHERE l.fetchedAt < :cutoff
        AND NOT EXISTS (
            SELECT 1 FROM WhItem d WHERE d.whListing = l
        )
    """)
    void deleteStaleListings(@Param("cutoff") LocalDateTime cutoff);
}
