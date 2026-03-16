package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DlExtractionRunRepository extends JpaRepository<DlExtractionRun, Long> {
    List<DlExtractionRun> findByItemText(ItemText itemText);
    List<DlExtractionRun> findByItemTextId(Long itemTextId);
    boolean existsByItemTextAndModelConfigAndStatus(ItemText itemText, DlModelConfig modelConfig, ExtractionStatus status);

    @Query("SELECT r FROM DlExtractionRun r JOIN FETCH r.itemText JOIN FETCH r.modelConfig WHERE r.status = :status AND r.createdAt < :before")
    List<DlExtractionRun> findByStatusAndCreatedAtBeforeEager(@Param("status") ExtractionStatus status, @Param("before") LocalDateTime before);

    @Query("SELECT r FROM DlExtractionRun r JOIN FETCH r.itemText JOIN FETCH r.modelConfig WHERE r.status = :status")
    List<DlExtractionRun> findByStatusEager(@Param("status") ExtractionStatus status);
}
