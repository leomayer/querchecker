package at.querchecker.research.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "category_spec_preference_field")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpecPreferenceField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preference_id", nullable = false)
    private CategorySpecPreference preference;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_source", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FieldSource fieldSource = FieldSource.USER;
}
