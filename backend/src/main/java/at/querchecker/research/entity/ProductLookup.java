package at.querchecker.research.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "product_lookup")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String lookupTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LookupStatus lookupStatus;

    @Column(columnDefinition = "TEXT")
    private String quickFactsJson;

    private LocalDateTime quickFactsFetchedAt;

    private String icecatId;

    @Column(length = 500)
    private String icecatUrl;

    @Column(columnDefinition = "TEXT")
    private String icecatSpecsJson;

    private LocalDateTime icecatFetchedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_lookup_source_url",
                     joinColumns = @JoinColumn(name = "product_lookup_id"))
    @Column(name = "source_url", length = 500)
    private List<String> sourceUrls;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
