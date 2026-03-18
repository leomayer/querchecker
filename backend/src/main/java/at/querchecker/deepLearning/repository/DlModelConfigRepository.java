package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.entity.DlModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DlModelConfigRepository extends JpaRepository<DlModelConfig, Long> {
    List<DlModelConfig> findByActiveTrueOrderByExecutionOrderAsc();
}
