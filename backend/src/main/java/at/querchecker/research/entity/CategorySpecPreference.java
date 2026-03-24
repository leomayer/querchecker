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

    @OneToMany(mappedBy = "preference", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.EAGER)
    @Builder.Default
    private List<CategorySpecPreferenceField> fields = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    /** USER-Felder als editierbare Keywords (für Settings-UI und Brave-Query). */
    public List<String> getUserFieldKeys() {
        return fields.stream()
                .filter(f -> f.getFieldSource() == FieldSource.USER)
                .map(CategorySpecPreferenceField::getFieldKey)
                .toList();
    }
}
