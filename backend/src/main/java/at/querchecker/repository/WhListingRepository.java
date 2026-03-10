package at.querchecker.repository;

import at.querchecker.entity.WhListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhListingRepository extends JpaRepository<WhListing, Long> {

    Optional<WhListing> findByWhId(String whId);

    List<WhListing> findAllByWhIdIn(Collection<String> whIds);
}
