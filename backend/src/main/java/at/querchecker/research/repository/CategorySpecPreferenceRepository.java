package at.querchecker.research.repository;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySpecPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategorySpecPreferenceRepository extends JpaRepository<CategorySpecPreference, Long> {
    Optional<CategorySpecPreference> findByWhCategory(WhCategory category);
}
