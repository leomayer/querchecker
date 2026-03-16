package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DlExtractionTermRepository extends JpaRepository<DlExtractionTerm, Long> {
    void deleteByRun(DlExtractionRun run);

    @Query("""
        SELECT t FROM DlExtractionTerm t
        JOIN FETCH t.run r JOIN FETCH r.modelConfig
        WHERE r.itemText.id = :itemTextId
        ORDER BY t.confidence DESC
        """)
    List<DlExtractionTerm> findByItemTextId(@Param("itemTextId") Long itemTextId);
}
