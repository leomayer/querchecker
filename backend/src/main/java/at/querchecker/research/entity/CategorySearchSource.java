package at.querchecker.research.entity;

import at.querchecker.entity.WhCategory;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "category_search_source")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySearchSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;   // null = Default

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private String siteDomain;

    @Column(nullable = false)
    private String siteLabel;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(columnDefinition = "text[]")
    @Type(ListArrayType.class)
    private List<String> queryExcludes; // Negativ-Operatoren für Brave-Query
                                        // Icecat:       ["-filetype:pdf", "-\"user guide\"", ...]
                                        // FlatpanelsHD: ["-review.php", "-news.php", ...]
                                        // null = keine Ausschlüsse

    @Column(nullable = false)
    @Builder.Default
    private int searchResultCount = 10;
    // 10 = Snippets-Pfad (alle ans LLM)
    //  3 = HTML-Fetch-Pfad (Top-URL + 2 Fallbacks)

    @Column(nullable = false)
    @Builder.Default
    private boolean lookupEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean inheritFromParent = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
