package at.querchecker.deepLearning.repository;

import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.entity.WhCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DlCategoryPromptRepository extends JpaRepository<DlCategoryPrompt, Long> {

    Optional<DlCategoryPrompt> findByWhCategoryAndPromptType(
        WhCategory category, PromptType promptType);

    @Query("SELECT p FROM DlCategoryPrompt p " +
           "WHERE p.whCategory IS NULL AND p.promptType = :promptType")
    Optional<DlCategoryPrompt> findDefaultByPromptType(
        @Param("promptType") PromptType promptType);

    long countByPromptType(PromptType promptType);
}
