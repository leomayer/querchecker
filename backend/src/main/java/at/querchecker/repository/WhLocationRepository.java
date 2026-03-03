package at.querchecker.repository;

import at.querchecker.entity.WhLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhLocationRepository extends JpaRepository<WhLocation, Long> {
    Optional<WhLocation> findByAreaId(Integer areaId);
    List<WhLocation> findByLevelOrderByNameAsc(Integer level);
    List<WhLocation> findByParentIdOrderByNameAsc(Long parentId);
}
