package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.entity.WhListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ItemTextRepository extends JpaRepository<ItemText, Long> {
    Optional<ItemText> findFirstByWhListingOrderByFetchedAtDesc(WhListing listing);

    @Modifying
    @Query("""
        DELETE FROM ItemText it
        WHERE it.fetchedAt < :cutoff
        AND it.id NOT IN (
            SELECT MIN(it2.id) FROM ItemText it2
            WHERE it2.whListing = it.whListing
            GROUP BY it2.whListing
            HAVING MAX(it2.fetchedAt) = MAX(it2.fetchedAt)
        )
        AND it.id NOT IN (
            SELECT t.run.itemText.id FROM DlExtractionTerm t
            WHERE t.userCorrectedTerm IS NOT NULL
        )
        """)
    void deleteOutdatedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
