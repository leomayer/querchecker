package at.querchecker.research.repository;

import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorySearchSourceRepository extends JpaRepository<CategorySearchSource, Long> {

    List<CategorySearchSource> findByWhCategoryAndActiveTrueOrderByPriorityAsc(
            WhCategory category);

    List<CategorySearchSource> findByWhCategoryAndInheritFromParentTrueAndActiveTrueOrderByPriorityAsc(
            WhCategory category);

    Optional<CategorySearchSource> findByWhCategoryAndSiteDomain(
            WhCategory category, String siteDomain);
}
