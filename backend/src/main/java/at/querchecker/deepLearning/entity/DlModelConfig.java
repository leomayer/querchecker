package at.querchecker.deepLearning.entity;

import at.querchecker.deepLearning.ModelSource;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dl_model_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String modelName;

    private String modelVersion;

    @Column(nullable = false)
    private Float temperature;

    @Column(nullable = false)
    private Integer maxTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelSource source;

    private String localPath;

    @Column(nullable = false)
    private Boolean active;
}
