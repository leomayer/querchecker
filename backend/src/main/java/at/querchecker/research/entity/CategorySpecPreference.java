package at.querchecker.research.entity;

import at.querchecker.entity.WhCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "category_spec_preference")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpecPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_spec_preference_field",
                     joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "field_key")
    @Builder.Default
    private List<String> fieldKeys = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
