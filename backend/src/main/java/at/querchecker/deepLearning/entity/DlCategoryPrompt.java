package at.querchecker.deepLearning.entity;

import at.querchecker.entity.WhCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dl_category_prompt")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlCategoryPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;  // null = Default-Prompt

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
