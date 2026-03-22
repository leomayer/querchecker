package at.querchecker.research.repository;

import at.querchecker.research.entity.ProductLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductLookupRepository extends JpaRepository<ProductLookup, Long> {
    Optional<ProductLookup> findByLookupTerm(String lookupTerm);
    Optional<ProductLookup> findByIcecatId(String icecatId);
}
