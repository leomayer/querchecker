package at.querchecker.deepLearning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "querchecker.dl")
@Data
public class DlConfig {

    private double minConfidence = 0.0;
    private int topK = 5;
    private ItemTextCleanup itemTextCleanup = new ItemTextCleanup();

    @Data
    public static class ItemTextCleanup {
        private long retentionDays = 10;
        private String cron = "0 0 3 * * *";
    }
}
