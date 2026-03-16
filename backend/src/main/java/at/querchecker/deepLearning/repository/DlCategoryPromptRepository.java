package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.entity.WhCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DlCategoryPromptRepository extends JpaRepository<DlCategoryPrompt, Long> {
    Optional<DlCategoryPrompt> findByWhCategory(WhCategory category);

    @Query("SELECT p FROM DlCategoryPrompt p WHERE p.whCategory IS NULL")
    Optional<DlCategoryPrompt> findDefault();
}
