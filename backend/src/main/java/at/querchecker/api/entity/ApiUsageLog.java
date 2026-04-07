package at.querchecker.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_usage_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestType requestType;

    private String lookupTerm;

    private Integer responseStatus;

    private Integer tokensInput;

    private Integer tokensOutput;

    private Long durationMs;

    /** LLM-Modellname (z.B. "llama-3.1-8b-instant"), null für Such-Provider */
    private String modelName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
