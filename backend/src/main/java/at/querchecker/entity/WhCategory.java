package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wh_category")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Willhaben ATTRIBUTE_TREE-Wert, z.B. 5882 für Grafikkarten. */
    @Column(nullable = false, unique = true)
    private Integer whId;

    @Column(nullable = false)
    private String name;

    /** 0 = Hauptkategorie, 1 = Unterkategorie, 2 = Unter-Unterkategorie, 3 = Tiefste Ebene */
    @Column(nullable = false)
    private Integer level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private WhCategory parent;
}
