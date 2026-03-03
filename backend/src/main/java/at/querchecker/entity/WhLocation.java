package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wh_location")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Willhaben areaId-Wert, z.B. 900 für Wien. */
    @Column(nullable = false, unique = true)
    private Integer areaId;

    @Column(nullable = false)
    private String name;

    /** 0 = Bundesland, 1 = Bezirk */
    @Column(nullable = false)
    private Integer level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private WhLocation parent;
}
