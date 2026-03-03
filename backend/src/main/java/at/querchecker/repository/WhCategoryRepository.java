package at.querchecker.repository;

import at.querchecker.entity.WhCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhCategoryRepository extends JpaRepository<WhCategory, Long> {
    Optional<WhCategory> findByWhId(Integer whId);
    List<WhCategory> findByLevelOrderByNameAsc(Integer level);
    List<WhCategory> findByParentIdOrderByNameAsc(Long parentId);
}
